package com.kryos.mascotas;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public class PetListener implements Listener {

    private final KryosMascotas plugin;
    private final PetManager pm;

    public PetListener(KryosMascotas plugin, PetManager pm) {
        this.plugin = plugin;
        this.pm = pm;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerPets pp = pm.data(p.getUniqueId());
        if (pp.active != null && plugin.getConfig().getBoolean("invocar-al-entrar", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && pp.active != null) pm.spawn(p, pp.active, true);
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pm.cancelRename(e.getPlayer().getUniqueId());
        pm.despawn(e.getPlayer().getUniqueId());
        pm.save();
    }

    /** Las mascotas nunca eligen objetivos por su cuenta (así no atacan a su dueño). */
    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (pm.isPet(e.getEntity())) e.setCancelled(true);
    }

    /** Si el dueño golpea algo, o algo lo golpea, la mascota va a por ese enemigo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        Entity victim = e.getEntity();
        if (pm.isPet(damager) || pm.isPet(victim)) return;

        if (damager instanceof Player attacker && victim instanceof LivingEntity livingVictim) {
            pm.setTarget(attacker, livingVictim);
        } else if (victim instanceof Player defender && damager instanceof LivingEntity livingAttacker) {
            pm.setTarget(defender, livingAttacker);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (pm.isPet(dead)) return;
        UUID byPet = pm.takeLastHit(dead.getUniqueId());
        Player killer = dead.getKiller();
        Player owner = killer != null ? killer : (byPet != null ? Bukkit.getPlayer(byPet) : null);
        if (owner == null || owner.equals(dead)) return;
        pm.awardKill(owner, dead, byPet != null);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (!pm.isPet(clicked)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (p.getUniqueId().equals(pm.petOwner(clicked))) {
            PlayerPets pp = pm.data(p.getUniqueId());
            if (pp.active != null && pm.petSec(pp.active) != null) {
                new PetMenu(plugin, pm, p, pp.active).open();
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (pm.isPet(en)) en.remove();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuBase menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        menu.click(e.getSlot(), e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuBase) e.setCancelled(true);
    }

    /** Captura el apodo cuando el jugador está renombrando a su mascota. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!pm.isRenaming(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> pm.handleRename(p, text));
    }
}
