package com.kryos.mascotas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Base común de los tres menús: utilidades, tooltips y textos compartidos. */
public abstract class MenuBase implements InventoryHolder {

    protected final KryosMascotas plugin;
    protected final PetManager pm;
    protected final Player player;
    protected Inventory inv;

    protected MenuBase(KryosMascotas plugin, PetManager pm, Player player) {
        this.plugin = plugin;
        this.pm = pm;
        this.player = player;
    }

    protected void create(int size, String title) {
        inv = Bukkit.createInventory(this, size, plugin.color(title));
    }

    public abstract void build();

    public abstract void click(int slot, ClickType type);

    public void open() {
        build();
        player.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    // ----------------------------------------------------------------- utilidades

    protected String title(String key, String def, String pet, int page, int pages) {
        return plugin.getConfig().getString(key, def)
                .replace("%mascota%", pet)
                .replace("%pagina%", String.valueOf(page))
                .replace("%paginas%", String.valueOf(pages));
    }

    protected void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    protected void sound(Sound s, float pitch) {
        player.playSound(player.getLocation(), s, 1f, pitch);
    }

    protected void tick() {
        sound(Sound.UI_BUTTON_CLICK, 1f);
    }

    protected void deny() {
        sound(Sound.ENTITY_VILLAGER_NO, 1f);
    }

    protected static Material material(String name, Material def) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    protected Material petIcon(String id) {
        return material(pm.petSec(id).getString("icono", "PAPER"), Material.PAPER);
    }

    protected ItemStack item(Material mat, String name, List<String> lore, boolean glow, int amount) {
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plugin.color(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(plugin::color).toList());
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    protected void fillAll() {
        Material m = material(plugin.getConfig().getString("menu.relleno", "BLACK_STAINED_GLASS_PANE"),
                Material.BLACK_STAINED_GLASS_PANE);
        ItemStack f = item(m, " ", List.of(), false, 1);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
    }

    protected ItemStack closeItem() {
        return item(Material.BARRIER, "<red>Cerrar", List.of("<gray>Cierra este menú."), false, 1);
    }

    protected static String fmt(double v, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    /** Entero si es casi entero, un decimal si no. */
    protected static String pct(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf((long) Math.rint(v)) : fmt(v, 1);
    }

    protected static String bar(double ratio) {
        int total = 20;
        int filled = (int) Math.max(0, Math.min(total, Math.round(ratio * total)));
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(total - filled);
    }

    protected static String bullet(String s) {
        return "<dark_gray>» <gray>" + s;
    }

    protected static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    protected static String effectName(String key) {
        return switch (key.trim().toUpperCase(Locale.ROOT)) {
            case "SPEED" -> "Velocidad";
            case "STRENGTH" -> "Fuerza";
            case "RESISTANCE" -> "Resistencia";
            case "JUMP_BOOST" -> "Salto mejorado";
            case "NIGHT_VISION" -> "Visión nocturna";
            case "LUCK" -> "Suerte";
            case "HASTE" -> "Prisa";
            case "HEALTH_BOOST" -> "Salud mejorada";
            case "FIRE_RESISTANCE" -> "Resistencia al fuego";
            case "REGENERATION" -> "Regeneración";
            case "ABSORPTION" -> "Absorción";
            case "WATER_BREATHING" -> "Respiración acuática";
            default -> {
                String s = key.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    protected static String abilityDesc(Map<?, ?> m) {
        String tipo = String.valueOf(m.get("tipo")).toUpperCase(Locale.ROOT);
        return switch (tipo) {
            case "FUEGO" -> "Prende en fuego a los enemigos al golpear";
            case "RAYO" -> "Invoca rayos al golpear <dark_gray>(<yellow>"
                    + (int) (PetManager.dbl(m, "probabilidad", 0.2) * 100) + "%<dark_gray>)";
            case "LENTITUD" -> "Ralentiza a los enemigos al golpear";
            case "VENENO" -> "Envenena a los enemigos al golpear";
            case "CURAR" -> "Te cura cuando tu vida es baja";
            case "ESCUDO" -> "Te protege con absorción en combate";
            default -> PetManager.abilityName(tipo);
        };
    }

    // ------------------------------------------------------------------- tooltips

    /** Beneficios que la mascota da en este nivel (estilo "Efectos:" de la referencia). */
    protected List<String> effectLines(String id, int level) {
        ConfigurationSection sec = pm.petSec(id);
        List<String> out = new ArrayList<>();
        double bonus = pm.bonusPercent(id, level);
        if (bonus > 0) out.add(bullet("Otorga <green>+" + pct(bonus) + "%<gray> de daño cuerpo a cuerpo"));
        for (Map<?, ?> m : sec.getMapList("habilidades")) {
            if (level >= PetManager.num(m, "nivel", 1)) out.add(bullet(abilityDesc(m)));
        }
        for (String s : sec.getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length < 3 || level < PetManager.parseInt(p[2], 1)) continue;
            out.add(bullet("Te da <green>" + effectName(p[0]) + " " + roman(PetManager.parseInt(p[1], 0) + 1)));
        }
        if (out.isEmpty()) out.add(bullet("Ninguno todavía"));
        return out;
    }

    protected List<String> progressLines(PlayerPets.Progress pr) {
        List<String> l = new ArrayList<>();
        if (pr.level >= pm.maxLevel()) {
            l.add("<dark_gray>» <gold>Nivel máximo alcanzado");
            return l;
        }
        double need = pm.xpNeeded(pr.level);
        l.add("<dark_gray>» <yellow>" + fmt(pr.xp / need * 100, 2) + "%");
        l.add("<dark_gray>» <yellow>" + (int) pr.xp + "/" + (int) need + " XP");
        l.add(bar(pr.xp / need));
        return l;
    }

    /** Tooltip completo de una mascota (sin el nombre, que va aparte). */
    protected List<String> petTooltip(String id, PlayerPets pp, List<String> footer) {
        ConfigurationSection sec = pm.petSec(id);
        List<String> l = new ArrayList<>();
        l.add("<gray><italic>" + sec.getString("descripcion", ""));
        l.add("");
        if (!pp.unlocked.contains(id)) {
            l.add("<red>Bloqueada");
            l.add("<yellow>Cómo desbloquear:");
            l.add("<dark_gray>» <white>" + sec.getString("desbloqueo", "???"));
            return l;
        }
        PlayerPets.Progress pr = pp.get(id);
        l.add("<white>Efectos:");
        l.addAll(effectLines(id, pr.level));
        l.add("");
        l.add("<white>Progreso:");
        l.addAll(progressLines(pr));
        if (!footer.isEmpty()) {
            l.add("");
            l.addAll(footer);
        }
        return l;
    }

    protected List<String> nextRewards(String petId, int level, int limit) {
        List<String> lore = new ArrayList<>();
        ConfigurationSection rs = plugin.getConfig().getConfigurationSection("recompensas");
        List<Integer> levels = new ArrayList<>();
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                int lv = PetManager.parseInt(k, 0);
                String only = rs.getString(k + ".solo-mascota");
                if (lv > level && (only == null || only.equalsIgnoreCase(petId))) levels.add(lv);
            }
        }
        levels.sort(Integer::compare);
        int shown = 0;
        for (int lv : levels) {
            if (shown++ >= limit) break;
            lore.add("<yellow>Nv." + lv + " <dark_gray>» <white>" + rs.getString(lv + ".descripcion", "Recompensa"));
        }
        if (lore.isEmpty()) lore.add("<gray>¡Ya tienes todas las recompensas!");
        return lore;
    }

    protected ItemStack particleItem(PlayerPets pp, String petId) {
        if (petId == null || pm.petSec(petId) == null) {
            return item(Material.FIREWORK_STAR, "<light_purple>Estela de partículas",
                    List.of("<gray>Invoca una mascota para elegir su estela."), false, 1);
        }
        PlayerPets.Progress pr = pp.get(petId);
        String actual = switch (pr.particle.toLowerCase(Locale.ROOT)) {
            case "auto" -> "automática (la mejor desbloqueada)";
            case "none" -> "ninguna";
            default -> pr.particle.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
        int total = pm.petSec(petId).getMapList("estela").size();
        return item(Material.FIREWORK_STAR, "<light_purple>Estela de partículas", List.of(
                "<white>Actual: <aqua>" + actual,
                "<gray>Desbloqueadas: <white>" + pm.unlockedParticles(petId, pr.level).size() + "<gray>/" + total,
                "",
                "<yellow>¡Clic para cambiar de estela!"), false, 1);
    }
}
