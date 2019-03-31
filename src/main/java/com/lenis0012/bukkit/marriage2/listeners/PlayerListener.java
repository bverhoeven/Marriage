package com.lenis0012.bukkit.marriage2.listeners;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lenis0012.bukkit.marriage2.MPlayer;
import com.lenis0012.bukkit.marriage2.Marriage;
import com.lenis0012.bukkit.marriage2.config.Message;
import com.lenis0012.bukkit.marriage2.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.concurrent.TimeUnit;

public class PlayerListener implements Listener {
    private final Marriage marriage;
    private final Cache<Player, ExperienceOrb.SpawnReason> expOrbSpawnReasonCache;

    public PlayerListener(Marriage marriage) {
        this.marriage = marriage;
        this.expOrbSpawnReasonCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.SECONDS).build();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity e0 = event.getEntity();
        final Entity e1 = event.getDamager();
        // Verify damaged entity is player
        if(!(e0 instanceof Player)) {
            return;
        } if(!(e1 instanceof Player) && !(e1 instanceof Projectile)) {
            return;
        }

        // Verify damager is player
        final Player player = (Player) e0;
        final Player damager;
        if(e1 instanceof Player) {
            damager = (Player) e1;
        } else {
            Projectile projectile = (Projectile) e1;
            final ProjectileSource e3 = projectile.getShooter();
            if(e3 == null || !(e3 instanceof Player)) {
                return;
            }
            damager = (Player) e3;
        }

        // Verify marriage
        MPlayer mplayer = marriage.getMPlayer(player);
        if(!mplayer.isMarried() || mplayer.getMarriage().getOtherPlayer(player.getUniqueId()) != damager.getUniqueId()) {
            return;
        }

        // Verify pvp setting
        if(mplayer.getMarriage().isPVPEnabled()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerPickupExp(PlayerPickupExperienceEvent event) {
        if(!Settings.EXP_BOOST_ENABLED.value()) return;

        // Experience boost for married player is enabled and the player picked up an experience orb. Store the reason
        // for it to spawn so that we can refer to it when they actually gain the extra experience, which should happen
        // immediately after this event.
        //
        // See the `onPlayerGainExp` function below for more information.

        expOrbSpawnReasonCache.put(event.getPlayer(), event.getExperienceOrb().getSpawnReason());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerGainExp(PlayerExpChangeEvent event) {
        if(!Settings.EXP_BOOST_ENABLED.value()) {
            return;
        }

        ExperienceOrb.SpawnReason orbSpawnReason = expOrbSpawnReasonCache.getIfPresent(event.getPlayer());

        // If an orb was recently picked up and the reason for that orb to spawn was an EXP bottle then do not give the
        // player bonus experience.
        //
        // Justification: There are plugins that will allow players to convert their experience into bottles and without
        // this check in place we'd allow players to generate an unlimited amount of experience by letting them turn EXP
        // into bottles, giving them bonus experience from using those bottles and then letting them convert that EXP
        // back into bottles, which they could endlessly repeat.

        if (ExperienceOrb.SpawnReason.EXP_BOTTLE == orbSpawnReason) {
            return;
        }

        final int gained = event.getAmount();
        if(gained <= 0) {
            return;
        }

        final Player player = event.getPlayer();
        final MPlayer mplayer = marriage.getMPlayer(player);
        if(!mplayer.isMarried()) {
            return;
        }

        MPlayer mpartner = mplayer.getPartner();
        Player partner = Bukkit.getPlayer(mpartner.getUniqueId());
        if(partner == null || !partner.isOnline()) {
            return;
        }

        if(!partner.getWorld().equals(player.getWorld())) {
            return;
        }

        if(partner.getLocation().distanceSquared(partner.getLocation()) > Settings.EXP_BOOST_DISTANCE.value() * Settings.EXP_BOOST_DISTANCE.value()) {
            return;
        }

        event.setAmount((int) Math.round(gained * Settings.EXP_BOOST_MULTIPLIER.value()));
        final int bonus = event.getAmount() - gained;
        if(bonus > 0 && Settings.EXP_BOOST_ANNOUNCE.value()) {
            Message.BONUS_EXP.send(player, bonus);
        }
    }
}
