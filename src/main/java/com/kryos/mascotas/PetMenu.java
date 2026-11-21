package com.kryos.mascotas;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Menú de una mascota concreta: habilidades, efectos, estelas, recompensas, apodo y control. */
public class PetMenu extends MenuBase {

    private final String petId;

    public PetMenu(KryosMascotas plugin, PetManager pm, Player player, String petId) {
        super(plugin, pm, player);
        this.petId = petId;
        PlayerPets.Progress pr = pm.data(player.getUniqueId()).get(petId);
        create(45, title("menu.titulo-mascota", "%mascota% <dark_gray>- Menú", pm.displayName(petId, pr), 1, 1));
    }

    @Override
    public void build() {
        inv.clear();
        fillAll();
        PlayerPets pp = pm.data(player.getUniqueId());
        ConfigurationSection sec = pm.petSec(petId);
        PlayerPets.Progress pr = pp.get(petId);
        boolean active = petId.equals(pp.active) && pm.hasActive(player);
        String name = pm.displayName(petId, pr);

        inv.setItem(4, item(petIcon(petId), name + " <white>Nv. <yellow>" + pr.level,
                petTooltip(petId, pp, List.of("<yellow>¡Clic para ver la progresión de niveles!")), active, 1));
        inv.setItem(20, abilitiesItem(sec, pr));
        inv.setItem(21, effectsItem(sec, pr));
        inv.setItem(22, particleItem(pp, petId));

        List<String> rl = new ArrayList<>(nextRewards(petId, pr.level, 5));
        rl.add("");
        rl.add("<yellow>¡Clic para ver todos los niveles!");
        inv.setItem(23, item(Material.CHEST, "<gold>Próximas recompensas", rl, false, 1));

        String nick = pr.nickname == null ? "<gray>ninguno" : name;
        inv.setItem(24, item(Material.NAME_TAG, "<green>Renombrar mascota", List.of(
                "<white>Nombre: " + nick,
                "<gray>Tipo: " + sec.getString("nombre", petId),
                "",
                "<yellow>¡Clic para ponerle un apodo!"), false, 1));

        List<String> st = new ArrayList<>();
        st.add("<white>Nivel: <yellow>" + pr.level + "<gray>/" + pm.maxLevel());
        st.addAll(progressLines(pr));
        st.add("");
        st.add("<white>Daño actual: <red>" + fmt(pm.damage(petId, pr.level), 1));
        st.add("<white>Enemigos derrotados: <yellow>" + pr.kills);
        inv.setItem(31, item(Material.EXPERIENCE_BOTTLE, "<yellow>Progreso", st, false, 1));

        inv.setItem(36, item(Material.ARROW, "<yellow>Volver", List.of("<gray>Lista de mascotas."), false, 1));
        inv.setItem(40, active
                ? item(Material.RED_DYE, "<red>Guardar mascota", List.of("<gray>Retira a tu mascota.", "", "<yellow>¡Clic para guardarla!"), false, 1)
                : item(Material.LIME_DYE, "<green>Invocar mascota", List.of("<gray>Haz que te acompañe.", "", "<yellow>¡Clic para invocarla!"), false, 1));
        inv.setItem(44, closeItem());
    }

    private ItemStack abilitiesItem(ConfigurationSection sec, PlayerPets.Progress pr) {
        List<String> l = new ArrayList<>();
        List<Map<?, ?>> habs = sec.getMapList("habilidades");
        if (habs.isEmpty()) l.add("<gray>Esta mascota no tiene habilidades.");
        for (Map<?, ?> m : habs) {
            int need = PetManager.num(m, "nivel", 1);
            String n = PetManager.abilityName(String.valueOf(m.get("tipo")));
            if (pr.level >= need) {
                l.add("<green>+ <white>" + n);
                l.add("<gray>   " + abilityDesc(m));
            } else {
                l.add("<dark_gray>- " + n + " (Nv." + need + ")");
            }
        }
        return item(Material.BLAZE_ROD, "<light_purple>Habilidades", l, false, 1);
    }

    private ItemStack effectsItem(ConfigurationSection sec, PlayerPets.Progress pr) {
        List<String> l = new ArrayList<>();
        List<String> efectos = sec.getStringList("efectos");
        if (efectos.isEmpty()) l.add("<gray>Esta mascota no da efectos.");
        for (String s : efectos) {
            String[] p = s.split(":");
            if (p.length < 3) continue;
            int need = PetManager.parseInt(p[2], 1);
            String n = effectName(p[0]) + " " + roman(PetManager.parseInt(p[1], 0) + 1);
            l.add(pr.level >= need ? "<green>+ <white>" + n : "<dark_gray>- " + n + " (Nv." + need + ")");
        }
        l.add("");
        l.add("<gray>Se aplican mientras tu mascota");
        l.add("<gray>está contigo.");
        return item(Material.BREWING_STAND, "<aqua>Efectos pasivos", l, false, 1);
    }

    @Override
    public void click(int slot, ClickType type) {
        PlayerPets pp = pm.data(player.getUniqueId());
        switch (slot) {
            case 4, 23, 31 -> {
                tick();
                later(() -> new LevelsMenu(plugin, pm, player, petId, -1).open());
            }
            case 22 -> {
                pm.cycleParticle(player, petId);
                sound(Sound.UI_BUTTON_CLICK, 1.4f);
                build();
            }
            case 24 -> {
                tick();
                pm.startRename(player, petId);
                later(player::closeInventory);
            }
            case 36 -> {
                tick();
                later(() -> new PetsMenu(plugin, pm, player, 0).open());
            }
            case 40 -> {
                boolean active = petId.equals(pp.active) && pm.hasActive(player);
                if (active) pm.dismiss(player);
                else pm.spawn(player, petId, false);
                tick();
                build();
            }
            case 44 -> later(player::closeInventory);
            default -> { }
        }
    }
}
