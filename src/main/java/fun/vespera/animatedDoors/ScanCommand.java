package fun.vespera.animatedDoors;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanCommand implements CommandExecutor {

    private final AnimatedDoors plugin;

    public ScanCommand(AnimatedDoors plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("animdoors.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage("§cUsage: /animdoors <world> <x> <z> <radius>");
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage("§cWorld \"" + args[0] + "\" not exist.");
            return true;
        }

        try {
            int centerX = Integer.parseInt(args[1]);
            int centerZ = Integer.parseInt(args[2]);
            int radiusBlocks = Integer.parseInt(args[3]);

            sender.sendMessage("§aStarted door replacing at world: \"" + world.getName() + "\"...");
            new ScannerTask(world, centerX, centerZ, radiusBlocks).runTaskTimer(plugin, 1L, 1L);

        } catch (NumberFormatException e) {
            sender.sendMessage("§cSyntaxis error in command.");
        }

        return true;
    }

    private class ScannerTask extends BukkitRunnable {
        private final World world;
        private final Queue<long[]> chunkQueue = new LinkedList<>();
        private final int totalChunks;
        private final AtomicInteger processedChunks = new AtomicInteger(0);
        private final AtomicInteger activeThreads = new AtomicInteger(0);
        private final long startTime;
        private long lastLogTime;

        public ScannerTask(World world, int centerX, int centerZ, int radiusBlocks) {
            this.world = world;

            // getting chunks coordinates by x, z
            int centerCx = centerX >> 4;
            int centerCz = centerZ >> 4;
            int radiusChunks = radiusBlocks >> 4;

            for (int x = centerCx - radiusChunks; x <= centerCx + radiusChunks; x++) {
                for (int z = centerCz - radiusChunks; z <= centerCz + radiusChunks; z++) {
                    chunkQueue.add(new long[]{x, z});
                }
            }

            this.totalChunks = chunkQueue.size();
            this.startTime = System.currentTimeMillis();
            this.lastLogTime = startTime;
            plugin.getLogger().info("Chunks to scan: " + totalChunks);
        }

        @Override
        public void run() {
            if (processedChunks.get() >= totalChunks) {
                plugin.getLogger().info("Door replacing in world: \"" + world.getName() + "\" completed.");
                this.cancel();
                return;
            }

            // limiting chunks load to prevent OutOfMemory (you can change this value for your purposes)
            while (activeThreads.get() < 10 && !chunkQueue.isEmpty()) {
                long[] coord = chunkQueue.poll();
                if (coord == null) break;

                int cx = (int) coord[0];
                int cz = (int) coord[1];

                activeThreads.incrementAndGet();

                // loading chunk
                world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    // scan chunk (async)
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                        int minY = world.getMinHeight();
                        int maxY = world.getMaxHeight();
                        List<Location> foundDoors = new ArrayList<>();

                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = minY; y < maxY; y++) {
                                    BlockData data = snapshot.getBlockData(x, y, z);
                                    if (data instanceof Door door && door.getHalf() == Bisected.Half.BOTTOM) {
                                        if (plugin.getModelData(data.getMaterial(), door.getHinge()) != -1) {
                                            foundDoors.add(new Location(world, (cx << 4) + x, y, (cz << 4) + z));
                                        }
                                    }
                                }
                            }
                        }

                        // spawn item_display (in main thread)
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (Location loc : foundDoors) {
                                Block block = loc.getBlock();
                                if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.BOTTOM) {
                                    plugin.spawnDoorDisplay(block, door);
                                }
                            }
                            processedChunks.incrementAndGet();
                            activeThreads.decrementAndGet();
                        });
                    });
                });
            }

            // logging status in console every 10 seconds
            long now = System.currentTimeMillis();
            if (now - lastLogTime >= 10000) {
                int processed = processedChunks.get();
                if (processed == 0) return;

                int percent = (int) ((processed * 100L) / totalChunks);
                long elapsed = now - startTime;
                double timePerChunk = (double) elapsed / processed;
                long remainingMillis = (long) (timePerChunk * (totalChunks - processed));

                String eta = formatTime(remainingMillis);
                plugin.getLogger().info(String.format("Door replacing running in world: %s. Processed: (%d%%), remaining time: %s", world.getName(), percent, eta));
                lastLogTime = now;
            }
        }

        private String formatTime(long millis) {
            long seconds = (millis / 1000) % 60;
            long minutes = (millis / (1000 * 60)) % 60;
            long hours = millis / (1000 * 60 * 60);
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }
}