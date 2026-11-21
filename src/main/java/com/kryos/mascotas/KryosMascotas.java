package com.kryos.mascotas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class KryosMascotas extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private PetManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new PetManager(this);
        manager.load();
        manager.cleanup();

        getServer().getPluginManager().registerEvents(new PetListener(this, manager), this);

        PetCommand cmd = new PetCommand(this, manager);
        PluginCommand pc = getCommand("mascota");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        manager.startTasks();
        getLogger().info("KryosMascotas activado.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public PetManager manager() {
        return manager;
    }

    /** MiniMessage sin cursiva (para nombres y lores). */
    public Component color(String text) {
        return MM.deserialize("<!italic>" + text);
    }

    public void msg(CommandSender to, String text) {
        String prefix = getConfig().getString("prefijo", "");
        to.sendMessage(MM.deserialize(prefix + text));
    }
}
