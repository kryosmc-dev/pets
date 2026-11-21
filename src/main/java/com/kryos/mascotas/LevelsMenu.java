package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Menú de progresión de niveles de una mascota (camino en zigzag, con páginas). */
public class LevelsMenu extends MenuBase {

    private static final int PER_PAGE = 28; // 4 filas x 7 columnas

    private final String petId;
    private final int page;
    private final int pages;

    /** page = -1 abre la página donde está el nivel actual. */
    public LevelsMenu(KryosMascotas plugin, PetManager pm, Player player, String petId, int page) {
        super(plugin, pm, player);
        this.petId = petId;
        this.pages = Math.max(1, (int) Math.ceil(pm.maxLevel() / (double) PER_PAGE));
        PlayerPets.Progress pr = pm.data(player.getUniqueId()).get(petId);
        int p = page < 0 ? (pr.level - 1) / PER_PAGE : page;
        this.page = Math.max(0, Math.min(p, pages - 1));
        create(54, title("menu.titulo-niveles", "%mascota% <dark_gray>- Niveles (%pagina%/%paginas%)",
                pm.displayName(petId, pr), this.page + 1, pages));
    }

    @Override
    public void build() {
        inv.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        PlayerPets.Progress pr = pp.get(petId);
        boolean active = petId.equals(pp.active) && pm.hasActive(player);
        int max = pm.maxLevel();

        inv.setItem(4, item(petIcon(petId), pm.displayName(petId, pr) + " <white>Nv. <yellow>" + pr.level,
                petTooltip(petId, pp, List.of("<yellow>¡Clic para abrir el menú de la mascota!")), active, 1));

        Material done = material(plugin.getConfig().getString("menu.nivel-desbloqueado", "LIME_STAINED_GLASS_PANE"),
                Material.LIME_STAINED_GLASS_PANE);
        Material prog = material(plugin.getConfig().getString("menu.nivel-progreso", "YELLOW_STAINED_GLASS_PANE"),
                Material.YELLOW_STAINED_GLASS_PANE);
        Material lock = material(plugin.getConfig().getString("menu.nivel-bloqueado", "RED_STAINED_GLASS_PANE"),
                Material.RED_STAINED_GLASS_PANE);

        String name = pm.displayName(petId, pr);
        for (int k = 0; k < PER_PAGE; k++) {
            int lvl = page * PER_PAGE + k + 1;
            if (lvl > max) continue;
            int row = k / 7;
            int col = (row % 2 == 0) ? k % 7 : 6 - (k % 7);
            int slot = (row + 1) * 9 + col + 1;

            List<String> lore = new ArrayList<>();
            lore.add("<white>Recompensas:");
            lore.addAll(levelRewards(lvl));
            lore.add("");
            Material mat;
            if (lvl <= pr.level) {
                mat = done;
                lore.add("<green><bold>DESBLOQUEADO");
                if (lvl == pr.level) lore.add("<gray>Tu nivel actual");
            } else if (lvl == pr.level + 1) {
                mat = prog;
                double need = pm.xpNeeded(pr.level);
                lore.add("<yellow><bold>EN PROGRESO");
                lore.add(bar(pr.xp / need));
                lore.add("<dark_gray>» <yellow>" + fmt(pr.xp / need * 100, 2) + "% <gray>(" + (int) pr.xp + "/" + (int) need + " XP)");
            } else {
                mat = lock;
                lore.add("<red><bold>BLOQUEADO");
            }
            inv.setItem(slot, item(mat, name + " <white>Nv. <yellow>" + lvl, lore, lvl == pr.level, lvl));
        }

        inv.setItem(45, item(Material.ARROW, "<yellow>Volver",
                List.of("<gray>Menú de la mascota."), false, 1));
        if (page > 0) {
            inv.setItem(48, item(Material.ARROW, "<yellow>Página anterior",
                    List.of("<gray>Ir a la página " + page + "."), false, 1));
        }
        inv.setItem(49, closeItem());
        if (page < pages - 1) {
            inv.setItem(50, item(Material.ARROW, "<yellow>Página siguiente",
                    List.of("<gray>Ir a la página " + (page + 2) + "."), false, 1));
        }
        inv.setItem(52, item(Material.COMPASS, "<aqua>Ir a mi nivel",
                List.of("<gray>Salta a la página de tu nivel actual."), false, 1));
    }

    private List<String> levelRewards(int lvl) {
        ConfigurationSection sec = pm.petSec(petId);
        List<String> out = new ArrayList<>();
        if (lvl == 1) out.add(bullet("Nivel inicial de la mascota"));
        double bonus = pm.bonusPercent(petId, lvl);
        if (lvl > 1 && bonus > 0) out.add(bullet("Otorga <green>+" + pct(bonus) + "%<gray> de daño cuerpo a cuerpo"));

        for (Map<?, ?> m : sec.getMapList("habilidades")) {
            if (PetManager.num(m, "nivel", 1) == lvl) {
                out.add(bullet("<light_purple>Habilidad: <white>" + PetManager.abilityName(String.valueOf(m.get("tipo")))));
            }
        }
        for (String s : sec.getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length >= 3 && PetManager.parseInt(p[2], 1) == lvl) {
                out.add(bullet("<aqua>Efecto: <white>" + effectName(p[0]) + " " + roman(PetManager.parseInt(p[1], 0) + 1)));
            }
        }
        for (Map<?, ?> m : sec.getMapList("estela")) {
            if (PetManager.num(m, "nivel", 1) == lvl) {
                out.add(bullet("<yellow>Estela: <white>" + String.valueOf(m.get("particula")).toLowerCase().replace('_', ' ')));
            }
        }
        ConfigurationSection r = plugin.getConfig().getConfigurationSection("recompensas." + lvl);
        if (r != null) {
            String only = r.getString("solo-mascota");
            if (only == null || only.equalsIgnoreCase(petId)) {
                String unlock = r.getString("desbloquear-mascota");
                if (unlock != null && pm.petSec(unlock) != null) {
                    out.add(bullet("<gold>Desbloquea: <white>" + pm.petSec(unlock).getString("nombre", unlock)));
                }
                String desc = r.getString("descripcion");
                if (desc != null && unlock == null) out.add(bullet("<green>Premio: <white>" + desc));
            }
        }
        if (out.isEmpty()) out.add(bullet("Aumenta el daño de tu mascota"));
        return out;
    }

    @Override
    public void click(int slot, ClickType type) {
        switch (slot) {
            case 4, 45 -> {
                tick();
                later(() -> new PetMenu(plugin, pm, player, petId).open());
            }
            case 48 -> {
                if (page > 0) {
                    tick();
                    later(() -> new LevelsMenu(plugin, pm, player, petId, page - 1).open());
                }
            }
            case 49 -> later(player::closeInventory);
            case 50 -> {
                if (page < pages - 1) {
                    tick();
                    later(() -> new LevelsMenu(plugin, pm, player, petId, page + 1).open());
                }
            }
            case 52 -> {
                tick();
                later(() -> new LevelsMenu(plugin, pm, player, petId, -1).open());
            }
            default -> { }
        }
    }
}
