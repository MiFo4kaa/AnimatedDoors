package fun.vespera.animatedDoors;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DoorListener implements Listener {

    private final AnimatedDoors plugin;

    public DoorListener(AnimatedDoors plugin) {
        this.plugin = plugin;
    }

    private void scheduleSync(Block block) {
        final Block lowerBlock = plugin.getLowerHalf(block);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ItemDisplay display = plugin.getDisplayForBlock(lowerBlock);
            if (display == null) return;

            if (!(lowerBlock.getBlockData() instanceof Door door)) {
                display.remove();
                return;
            }

            PersistentDataContainer pdc = display.getPersistentDataContainer();
            byte currentState = pdc.getOrDefault(AnimatedDoors.DOOR_STATE, PersistentDataType.BYTE, (byte) 0);
            byte actualState = (byte) (door.isOpen() ? 1 : 0);

            if (currentState != actualState) {
                pdc.set(AnimatedDoors.DOOR_STATE, PersistentDataType.BYTE, actualState);

                float yaw = plugin.getBaseYaw(door.getFacing(), door.getHinge());
                if (actualState == 1) {
                    yaw += (door.getHinge() == Door.Hinge.RIGHT) ? 90f : -90f;
                }

                Location targetLoc = display.getLocation().clone();
                targetLoc.setYaw(yaw);
                display.teleport(targetLoc);
            }
        }, 1L);
    }

    // spawn item_display on player interaction
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block lowerBlock = plugin.getLowerHalf(event.getBlockPlaced());
        if (lowerBlock.getBlockData() instanceof Door door) {
            plugin.spawnDoorDisplay(lowerBlock, door); // main spawn logic in AnimatedDoors.java
        }
    }

    // animate item_display on player interaction
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Door door)) return;

        scheduleSync(block);

        if (block.getType() == org.bukkit.Material.IRON_DOOR && !event.getPlayer().isSneaking()) return;
        if (event.getPlayer().isSneaking() && event.getItem() != null) return;

        Block lowerBlock = plugin.getLowerHalf(block);
        BlockFace partnerFace = plugin.getPartnerFace(door.getFacing(), door.getHinge());

        // open partner door if there is partner door
        if (partnerFace != null) {
            Block adjacentBlock = lowerBlock.getRelative(partnerFace);
            if (adjacentBlock.getType() == block.getType() && adjacentBlock.getBlockData() instanceof Door adjDoor) {
                if (adjDoor.getFacing() == door.getFacing() && adjDoor.getHinge() != door.getHinge()) {
                    if (adjDoor.isOpen() == door.isOpen()) {
                        boolean targetOpenState = !door.isOpen();
                        adjDoor.setOpen(targetOpenState);
                        adjacentBlock.setBlockData(adjDoor);
                        scheduleSync(adjacentBlock);
                    }
                }
            }
        }
    }

    // replacing doors on new chunks
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // finding door (async, to prevent tps drops)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
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
                                foundDoors.add(new Location(world, (chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z));
                            }
                        }
                    }
                }
            }

            if (!foundDoors.isEmpty()) {
                // spawning doors (in main thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (Location loc : foundDoors) {
                        Block block = loc.getBlock();
                        if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.BOTTOM) {
                            plugin.spawnDoorDisplay(block, door);
                        }
                    }
                });
            }
        });
    }

    // animate item_display on redstone activation
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (event.getBlock().getBlockData() instanceof Door) scheduleSync(event.getBlock());
    }

    // animate item_display on villager interaction
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.getBlock().getBlockData() instanceof Door) scheduleSync(event.getBlock());
    }

    // remove item_display on block break
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getBlockData() instanceof Door) {
            ItemDisplay display = plugin.getDisplayForBlock(plugin.getLowerHalf(event.getBlock()));
            if (display != null) display.remove();
        }
    }

    // remove item_display if someone digged block under door
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getBlockData() instanceof Door) scheduleSync(event.getBlock());
    }
}