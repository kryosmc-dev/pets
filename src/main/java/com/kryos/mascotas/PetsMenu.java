package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menú general: lista de mascotas con páginas. */
public class PetsMenu extends MenuBase {

    private static final int[] PET_SLOTS = new int[21];

    static {
        int i = 0;
        for (int r = 1; r <= 3; r++) {
            for (int c = 1; c <= 7; c++) PET_SLOTS[i++] = r * 9 + c;
        }
    }

    private final List<String> ids;
    private final int page;
    private final int pages;
    private final Map<Integer, String> slotToPet = new HashMap<>();

    public PetsMenu(KryosMascotas plugin, PetManager pm, Player player, int page) {
        super(plugin, pm, player);
        this.ids = new ArrayList<>(pm.petIds());
        this.pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PET_SLOTS.length));
        this.page = Math.max(0, Math.min(page, pages - 1));
        create(54, title("menu.titulo-general",
                "<gold><bold>Mascotas</bold> <dark_gray>(%pagina%/%paginas%)", "", this.page + 1, pages));
    }

    @Override
    public void build() {
        inv.clear();
        slotToPet.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        for (int s : PET_SLOTS) inv.setItem(s, null);

        int start = page * PET_SLOTS.length;
        for (int i = 0; i < PET_SLOTS.length; i++) {
            if (start + i >= ids.size()) break;
            String id = ids.get(start + i);
            boolean owned = pp.unlocked.contains(id);
            boolean active = owned && id.equals(pp.active) && pm.hasActive(player);
            PlayerPets.Progress pr = pp.get(id);

            String name;
            List<String> footer = new ArrayList<>();
            if (owned) {
                name = pm.displayName(id, pr) + " <white>Nv. <yellow>" + pr.level;
                if (active) {
                    footer.add("<green>Mascota activa");
                    footer.add("<yellow>¡Clic para ver la progresión de niveles!");
                } else {
                    footer.add("<yellow>¡Clic para activar esta mascota!");
                }
                footer.add("<gray>Clic derecho: <white>menú de la mascota");
            } else {
                name = "<dark_gray>[Bloqueada] " + pm.petSec(id).getString("nombre", id);
            }
            Material mat = owned ? petIcon(id) : Material.BARRIER;
            inv.setItem(PET_SLOTS[i], item(mat, name, petTooltip(id, pp, footer), active, 1));
            slotToPet.put(PET_SLOTS[i], id);
        }

        // ---- barra inferior
        boolean has = pm.hasActive(player);
        inv.setItem(45, has
                ? item(Material.RED_DYE, "<red>Guardar mascota",
                        List.of("<gray>Retira a tu mascota activa.", "", "<yellow>¡Clic para guardarla!"), false, 1)
                : item(Material.GRAY_DYE, "<gray>Guardar mascota",
                        List.of("<dark_gray>No tienes ninguna mascota invocada."), false, 1));
        inv.setItem(46, particleItem(pp, pp.active));
        inv.setItem(47, rewardsItem(pp));

        if (page > 0) {
            inv.setItem(48, item(Material.ARROW, "<yellow>Página anterior",
                    List.of("<gray>Ir a la página " + page + "."), false, 1));
        }
        inv.setItem(49, closeItem());
        if (page < pages - 1) {
            inv.setItem(50, item(Material.ARROW, "<yellow>Página siguiente",
                    List.of("<gray>Ir a la página " + (page + 2) + "."), false, 1));
        }
        inv.setItem(51, statsItem(pp));
        inv.setItem(53, pp.active != null && pm.petSec(pp.active) != null
                ? item(Material.LEAD, "<gold>Menú de tu mascota",
                        List.of("<gray>Habilidades, efectos, estelas,", "<gray>apodo y más.", "", "<yellow>¡Clic para abrir!"), false, 1)
                : item(Material.GRAY_DYE, "<gray>Menú de tu mascota",
                        List.of("<dark_gray>Invoca una mascota primero."), false, 1));
    }

    private org.bukkit.inventory.ItemStack rewardsItem(PlayerPets pp) {
        if (pp.active == null || pm.petSec(pp.active) == null) {
            return item(Material.BOOK, "<gold>Próximas recompensas",
                    List.of("<gray>Invoca una mascota para ver", "<gray>sus próximas recompensas."), false, 1);
        }
        List<String> lore = new ArrayList<>(nextRewards(pp.active, pp.get(pp.active).level, 5));
        lore.add("");
        lore.add("<yellow>¡Clic para ver todos los niveles!");
        return item(Material.BOOK, "<gold>Próximas recompensas", lore, false, 1);
    }

    private org.bukkit.inventory.ItemStack statsItem(PlayerPets pp) {
        int owned = 0;
        int levels = 0;
        int kills = 0;
        for (String id : ids) {
            if (!pp.unlocked.contains(id)) continue;
            owned++;
            levels += pp.get(id).level;
            kills += pp.get(id).kills;
        }
        String act = pp.active != null && pm.petSec(pp.active) != null
                ? pm.displayName(pp.active, pp.get(pp.active)) : "<gray>ninguna";
        return item(Material.NETHER_STAR, "<aqua>Estadísticas", List.of(
                "<white>Mascotas desbloqueadas: <yellow>" + owned + "<gray>/" + ids.size(),
                "<white>Niveles totales: <yellow>" + levels,
                "<white>Enemigos derrotados: <yellow>" + kills,
                "<white>Mascota activa: " + act), false, 1);
    }

    @Override
    public void click(int slot, ClickType type) {
        PlayerPets pp = pm.data(player.getUniqueId());
        String id = slotToPet.get(slot);

        if (id != null) {
            if (!pp.unlocked.contains(id)) {
                plugin.msg(player, "<red>Aún no has desbloqueado esta mascota.");
                deny();
                return;
            }
            tick();
            if (type.isRightClick() || type.isShiftClick()) {
                later(() -> new PetMenu(plugin, pm, player, id).open());
                return;
            }
            boolean active = id.equals(pp.active) && pm.hasActive(player);
            if (active) {
                later(() -> new LevelsMenu(plugin, pm, player, id, -1).open());
            } else {
                pm.spawn(player, id, false);
                sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
                build();
            }
            return;
        }

        switch (slot) {
            case 45 -> {
                if (pm.hasActive(player)) {
                    pm.dismiss(player);
                    tick();
                    build();
                }
            }
            case 46 -> {
                if (pp.active != null && pm.petSec(pp.active) != null) {
                    pm.cycleParticle(player, pp.active);
                    sound(Sound.UI_BUTTON_CLICK, 1.4f);
                    build();
                }
            }
            case 47 -> {
                if (pp.active != null && pm.petSec(pp.active) != null) {
                    tick();
                    String a = pp.active;
                    later(() -> new LevelsMenu(plugin, pm, player, a, -1).open());
                }
            }
            case 48 -> {
                if (page > 0) {
                    tick();
                    later(() -> new PetsMenu(plugin, pm, player, page - 1).open());
                }
            }
            case 49 -> later(player::closeInventory);
            case 50 -> {
                if (page < pages - 1) {
                    tick();
                    later(() -> new PetsMenu(plugin, pm, player, page + 1).open());
                }
            }
            case 53 -> {
                if (pp.active != null && pm.petSec(pp.active) != null) {
                    tick();
                    String a = pp.active;
                    later(() -> new PetMenu(plugin, pm, player, a).open());
                }
            }
            default -> { }
        }
    }
}
