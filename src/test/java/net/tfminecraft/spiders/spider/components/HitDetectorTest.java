package net.tfminecraft.spiders.spider.components;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HitDetectorTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    /** A projectile the test moves itself, as the server would after plugin ticks. */
    private static final class Flying<T extends Projectile> {
        Vector position;
        Vector velocity;
        boolean teleported = false;
        final T mock;

        Flying(Class<T> type, World world, Vector position, Vector velocity, ProjectileSource shooter) {
            this.position = position;
            this.velocity = velocity;
            T projectile = mock(type);
            when(projectile.isValid()).thenReturn(true);
            when(projectile.getShooter()).thenReturn(shooter);
            when(projectile.getLocation()).thenAnswer(call -> this.position.toLocation(world));
            when(projectile.getVelocity()).thenAnswer(call -> this.velocity.clone());
            doAnswer(call -> {
                this.velocity = call.<Vector>getArgument(0).clone();
                return null;
            }).when(projectile).setVelocity(any(Vector.class));
            when(projectile.teleport(any(Location.class))).thenAnswer(call -> {
                this.position = call.<Location>getArgument(0).toVector();
                teleported = true;
                return true;
            });
            this.mock = projectile;
        }

        void step() {
            position.add(velocity);
        }
    }

    private World field() {
        return new Room((x, y, z) -> x >= -20 && x <= 20 && z >= -20 && z <= 20 && y >= -3 && y <= -1).world();
    }

    private Run standingSpider() {
        return standingSpider(.8);
    }

    private Run standingSpider(double scale) {
        return standingSpider(scale, field());
    }

    private Run standingSpider(double scale, World world) {
        return standingSpider(scale, world, Collections::emptyList);
    }

    private Run standingSpider(Supplier<List<Entity>> nearby) {
        return standingSpider(.8, field(), nearby);
    }

    private Run standingSpider(double scale, Supplier<List<Entity>> nearby) {
        return standingSpider(scale, field(), nearby);
    }

    /** A settled spider with hit detection running and [nearby] entities around it. */
    private Run standingSpider(double scale, World world, Supplier<List<Entity>> nearby) {
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenAnswer(call -> nearby.get());
        Run run = spawn(world, scale, false, new Vector(.5, 0.0, .5), false);
        run.entity().addComponent(new HitDetector());
        HitDetector.setupProjectileCollisions(run.app());
        for (int i = 0; i < 30; i++) run.app().update();
        return run;
    }

    private record Shot(SpiderHitEvent event, Vector knock) {
    }

    private static List<SpiderHitEvent> collectHits(Run run) {
        List<SpiderHitEvent> hits = new ArrayList<>();
        run.app().onEvent(SpiderHitEvent.class, hits::add);
        return hits;
    }

    /** Flies [projectile] for up to 10 ticks; returns the hit and the spider's velocity change on the hit tick. */
    private static Shot fly(Run run, Flying<?> projectile) {
        List<SpiderHitEvent> hits = collectHits(run);
        for (int i = 0; i < 10; i++) {
            Vector before = run.body().getVelocity().clone();
            run.app().update();
            if (!hits.isEmpty()) return new Shot(hits.get(0), run.body().getVelocity().clone().subtract(before));
            projectile.step();
        }
        return new Shot(null, new Vector());
    }

    private static <T extends Projectile> Flying<T> shoot(Run run, Class<T> type, ProjectileSource shooter) {
        return shoot(run, type, shooter, 0.0);
    }

    /** A projectile coming at the middle of the body from 6 blocks along +z. */
    private static <T extends Projectile> Flying<T> shoot(Run run, Class<T> type, ProjectileSource shooter, double height) {
        return new Flying<>(type, run.body().getWorld(), run.body().getPosition().clone().add(new Vector(0.0, height, 6.0)), new Vector(0.0, 0.0, -2.5), shooter);
    }

    @Test
    @DisplayName("any archer's arrow hits the body, bounces off and pushes the spider")
    void anyArchersArrowHitsTheBodyBouncesOffAndPushesTheSpider() {
        List<ProjectileSource> shooters = Arrays.asList(mock(Player.class), mock(Skeleton.class), null);
        for (ProjectileSource shooter : shooters) {
            AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
            Run run = standingSpider(nearby::get);
            Flying<Arrow> arrow = shoot(run, Arrow.class, shooter);
            nearby.set(List.<Entity>of(arrow.mock));

            Shot shot = fly(run, arrow);
            SpiderHitEvent event = shot.event();
            assertNotNull(event, "The arrow from " + shooter + " flew through the body");
            assertTrue(event.getForceful());
            assertTrue(arrow.teleported);
            Vector body = run.body().getPosition();
            assertTrue(event.getPoint().getZ() > body.getZ() && event.getPoint().getZ() < body.getZ() + 1.5, "Hit at " + event.getPoint() + ", body at " + body);
            assertTrue(arrow.velocity.getZ() > 0 && arrow.velocity.length() < .5, "Arrow should bounce back weakly: " + arrow.velocity);
            assertTrue(shot.knock().getZ() < -.05, "Arrow from " + shooter + " did not push the spider: " + shot.knock());
        }
    }

    @Test
    @DisplayName("a weak arrow glances off without pushing")
    void aWeakArrowGlancesOffWithoutPushing() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run run = standingSpider(nearby::get);
        Flying<Arrow> arrow = new Flying<>(Arrow.class, run.body().getWorld(), run.body().getPosition().clone().add(new Vector(0.0, 0.0, 2.0)), new Vector(0.0, 0.0, -.6), null);
        nearby.set(List.<Entity>of(arrow.mock));

        Shot shot = fly(run, arrow);
        assertNotNull(shot.event());
        assertFalse(shot.event().getForceful());
        assertTrue(shot.knock().length() < .03, "Spider was pushed: " + shot.knock());
    }

    @Test
    @DisplayName("a trident pushes harder than an arrow")
    void aTridentPushesHarderThanAnArrow() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run tridentRun = standingSpider(nearby::get);
        Flying<Trident> trident = shoot(tridentRun, Trident.class, mock(Player.class));
        nearby.set(List.<Entity>of(trident.mock));
        double tridentKnock = -fly(tridentRun, trident).knock().getZ();

        AtomicReference<List<Entity>> arrows = new AtomicReference<>(Collections.emptyList());
        Run arrowRun = standingSpider(arrows::get);
        Flying<Arrow> arrow = shoot(arrowRun, Arrow.class, mock(Player.class));
        arrows.set(List.<Entity>of(arrow.mock));
        double arrowKnock = -fly(arrowRun, arrow).knock().getZ();

        assertTrue(tridentKnock > 2 * arrowKnock, "Trident " + tridentKnock + " vs arrow " + arrowKnock);
        verify(trident.mock).setHasDealtDamage(true);
    }

    @Test
    @DisplayName("snowballs break on the spider with a light thump")
    void snowballsBreakOnTheSpiderWithALightThump() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run run = standingSpider(nearby::get);
        Flying<Snowball> snowball = shoot(run, Snowball.class, mock(Player.class));
        nearby.set(List.<Entity>of(snowball.mock));

        Shot shot = fly(run, snowball);
        assertNotNull(shot.event());
        assertTrue(shot.event().getForceful());
        verify(snowball.mock).remove();
        assertTrue(shot.knock().getZ() < 0 && shot.knock().getZ() > -.05, "Snowball push " + shot.knock());
    }

    @Test
    @DisplayName("wind charges burst on the spider and shove it")
    void windChargesBurstOnTheSpiderAndShoveIt() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run run = standingSpider(nearby::get);
        Flying<WindCharge> charge = shoot(run, WindCharge.class, mock(Player.class));
        nearby.set(List.<Entity>of(charge.mock));

        Shot shot = fly(run, charge);
        assertNotNull(shot.event());
        verify(charge.mock).explode();
        assertTrue(shot.knock().length() > .3, "Wind charge shove " + shot.knock());
    }

    @Test
    @DisplayName("a rider's own shots pass through their mount")
    void aRidersOwnShotsPassThroughTheirMount() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run run = standingSpider(nearby::get);
        Player rider = mock(Player.class);
        ArmorStand marker = mock(ArmorStand.class);
        when(marker.getPassengers()).thenReturn(List.of(rider));
        Mountable mountable = new Mountable();
        mountable.setCurrentMarker(marker);
        run.entity().addComponent(mountable);
        Flying<Arrow> arrow = shoot(run, Arrow.class, rider);
        nearby.set(List.<Entity>of(arrow.mock));

        assertNull(fly(run, arrow).event());
    }

    @Test
    @DisplayName("arrows hit the legs too")
    void arrowsHitTheLegsToo() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run run = standingSpider(nearby::get);
        Leg leg = run.body().getLegs().get(0);
        Vector target = leg.getIk().getNodes().get(1).getPosition().clone().add(leg.getIk().getNodes().get(2).getPosition()).multiply(.5);
        double side = target.getX() > run.body().getPosition().getX() ? 1.0 : -1.0;
        Flying<Arrow> arrow = new Flying<>(Arrow.class, run.body().getWorld(), target.clone().add(new Vector(side * 6, 0.0, 0.0)), new Vector(-side * 2.5, 0.0, 0.0), null);
        nearby.set(List.<Entity>of(arrow.mock));

        SpiderHitEvent event = fly(run, arrow).event();
        assertNotNull(event, "The arrow passed through the leg");
        assertTrue(event.getPoint().distance(target) < .4, "Hit " + event.getPoint() + ", leg at " + target);
    }

    @Test
    @DisplayName("arrows over the spider fly on")
    void arrowsOverTheSpiderFlyOn() {
        AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
        Run run = standingSpider(nearby::get);
        Flying<Arrow> arrow = shoot(run, Arrow.class, null, 3.0);
        nearby.set(List.<Entity>of(arrow.mock));

        assertNull(fly(run, arrow).event());
        assertFalse(arrow.teleported);
    }

    @Test
    @DisplayName("every size is solid")
    void everySizeIsSolid() {
        for (double scale : List.of(.25, .35, 1.5, 2.5)) {
            AtomicReference<List<Entity>> nearby = new AtomicReference<>(Collections.emptyList());
            Run run = standingSpider(scale, nearby::get);
            Vector body = run.body().getPosition().clone();
            Flying<Arrow> arrow = new Flying<>(Arrow.class, run.body().getWorld(), body.clone().add(new Vector(0.0, 0.0, 8.0)), new Vector(0.0, 0.0, -2.5), null);
            nearby.set(List.<Entity>of(arrow.mock));
            SpiderHitEvent event = fly(run, arrow).event();
            assertNotNull(event, "Scale " + scale + ": the arrow flew through");
            assertTrue(event.getPoint().distance(body) < 2.0 * scale + .3, "Scale " + scale + ": hit " + event.getPoint() + ", body " + body);
        }
    }

    @Test
    @DisplayName("projectiles pass through the hidden seat entities")
    @SuppressWarnings("removal") // the short ProjectileHitEvent constructor is enough for a listener test
    void projectilesPassThroughTheHiddenSeatEntities() {
        ECS app = new ECS();
        Pig pig = mock(Pig.class);
        ArmorStand marker = mock(ArmorStand.class);
        Mountable mountable = new Mountable();
        mountable.setCurrentPig(pig);
        mountable.setCurrentMarker(marker);
        app.spawn(mountable);
        SeatProjectileListener listener = new SeatProjectileListener(app);

        for (Entity seat : List.<Entity>of(pig, marker)) {
            ProjectileHitEvent event = new ProjectileHitEvent(mock(Arrow.class), seat);
            listener.onHit(event);
            assertTrue(event.isCancelled(), "Arrow stopped by " + seat);
        }
        ProjectileHitEvent other = new ProjectileHitEvent(mock(Arrow.class), mock(Pig.class));
        listener.onHit(other);
        assertEquals(false, other.isCancelled());
    }

    // --- melee

    private Player fighter(World world, Vector eye, Vector look) {
        return fighter(world, eye, look, 1f);
    }

    private Player fighter(World world, Vector eye, Vector look, float charge) {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAttackCooldown()).thenReturn(charge);
        when(player.getEyeLocation()).thenAnswer(call -> eye.toLocation(world).setDirection(look));
        return player;
    }

    private static Pair<SpiderHitEvent, Vector> swing(Run run, Player player) {
        List<SpiderHitEvent> hits = collectHits(run);
        Vector before = run.body().getVelocity().clone();
        new MeleeListener(run.app(), p -> 3.0).onSwing(new PlayerArmSwingEvent(player, EquipmentSlot.HAND));
        return new Pair<>(hits.isEmpty() ? null : hits.get(0), run.body().getVelocity().clone().subtract(before));
    }

    @Test
    @DisplayName("a player's swing within reach hits and pushes the spider")
    void aPlayersSwingWithinReachHitsAndPushesTheSpider() {
        Run run = standingSpider();
        Vector eye = run.body().getPosition().clone().add(new Vector(0.0, .3, 3.2));
        Player player = fighter(run.body().getWorld(), eye, new Vector(0.0, -.1, -1.0));

        Pair<SpiderHitEvent, Vector> result = swing(run, player);
        SpiderHitEvent event = result.first();
        Vector knock = result.second();
        assertNotNull(event, "Swing missed the spider");
        assertTrue(event.getForceful());
        assertTrue(knock.getZ() < -.05, "Swing did not push: " + knock);
        verify(player).resetCooldown();
    }

    @Test
    @DisplayName("a weak swing pushes less and a spam swing does nothing")
    void aWeakSwingPushesLessAndASpamSwingDoesNothing() {
        Run strong = standingSpider();
        Vector strongKnock = swing(strong, fighter(strong.body().getWorld(), strong.body().getPosition().clone().add(new Vector(0.0, .3, 3.2)), new Vector(0.0, -.1, -1.0))).second();
        Run weak = standingSpider();
        Vector weakKnock = swing(weak, fighter(weak.body().getWorld(), weak.body().getPosition().clone().add(new Vector(0.0, .3, 3.2)), new Vector(0.0, -.1, -1.0), .5f)).second();
        assertTrue(-weakKnock.getZ() < -strongKnock.getZ() * .7, "Half charge " + weakKnock + " vs full " + strongKnock);

        Run spam = standingSpider();
        SpiderHitEvent event = swing(spam, fighter(spam.body().getWorld(), spam.body().getPosition().clone().add(new Vector(0.0, .3, 3.2)), new Vector(0.0, -.1, -1.0), .1f)).first();
        assertNull(event);
    }

    @Test
    @DisplayName("swings out of reach or into a wall miss")
    void swingsOutOfReachOrIntoAWallMiss() {
        Run run = standingSpider();
        Player far = fighter(run.body().getWorld(), run.body().getPosition().clone().add(new Vector(0.0, .3, 6.0)), new Vector(0.0, 0.0, -1.0));
        assertNull(swing(run, far).first(), "Hit from 6 blocks with a reach of 3");

        // a wall between the player and the spider
        World walled = new Room((x, y, z) -> x >= -20 && x <= 20 && z >= -20 && z <= 20 && ((y >= -3 && y <= -1) || (z == 3 && y >= 0 && y <= 3))).world();
        Run behind = standingSpider(.8, walled);
        Player player = fighter(walled, behind.body().getPosition().clone().add(new Vector(0.0, .3, 3.6)), new Vector(0.0, -.05, -1.0));
        assertNull(swing(behind, player).first(), "Hit through a wall");
    }

    @Test
    @DisplayName("attacking the hidden saddle pig counts as hitting the spider")
    void attackingTheHiddenSaddlePigCountsAsHittingTheSpider() {
        Run run = standingSpider();
        Pig pig = mock(Pig.class);
        when(pig.getLocation()).thenAnswer(call -> run.body().getPosition().clone().add(new Vector(0.0, -.6, 0.0)).toLocation(run.body().getWorld()));
        Mountable mountable = new Mountable();
        mountable.setCurrentPig(pig);
        run.entity().addComponent(mountable);
        // looking straight down at the pig's top from above: the swing ray may miss the drawn shape
        Player player = fighter(run.body().getWorld(), run.body().getPosition().clone().add(new Vector(0.0, 2.5, 1.0)), new Vector(0.0, -1.0, 0.0));
        List<SpiderHitEvent> hits = collectHits(run);

        PrePlayerAttackEntityEvent event = new PrePlayerAttackEntityEvent(player, pig, true);
        new MeleeListener(run.app(), p -> 3.0).onAttackSeat(event);
        assertTrue(event.isCancelled(), "The pig attack should be replaced by a spider hit");
        assertEquals(1, hits.size());
    }
}
