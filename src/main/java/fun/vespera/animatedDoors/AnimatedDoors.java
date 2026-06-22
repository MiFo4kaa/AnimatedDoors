package fun.vespera.animatedDoors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collection;

public class AnimatedDoors extends JavaPlugin {

    public static NamespacedKey DOOR_KEY, DOOR_X, DOOR_Y, DOOR_Z, DOOR_STATE;

    @Override
    public void onEnable() {
        DOOR_KEY = new NamespacedKey(this, "is_anim_door");
        DOOR_X = new NamespacedKey(this, "door_x");
        DOOR_Y = new NamespacedKey(this, "door_y");
        DOOR_Z = new NamespacedKey(this, "door_z");
        DOOR_STATE = new NamespacedKey(this, "door_state");

        getServer().getPluginManager().registerEvents(new DoorListener(this), this);
        getCommand("animdoors").setExecutor(new ScanCommand(this));

        String version = getPluginMeta().getVersion();
        getLogger().info("Animated Doors v" + version + " loaded");
    }


    public Block getLowerHalf(Block block) {
        if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    public ItemDisplay getDisplayForBlock(Block lowerBlock) {
        Location searchCenter = lowerBlock.getLocation().add(0.5, 1.890, 0.5);
        Collection<ItemDisplay> displays = lowerBlock.getWorld().getNearbyEntitiesByType(ItemDisplay.class, searchCenter, 2.5);

        for (ItemDisplay display : displays) {
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (pdc.has(DOOR_KEY, PersistentDataType.BYTE)) {
                if (pdc.getOrDefault(DOOR_X, PersistentDataType.INTEGER, 0) == lowerBlock.getX() &&
                        pdc.getOrDefault(DOOR_Y, PersistentDataType.INTEGER, 0) == lowerBlock.getY() &&
                        pdc.getOrDefault(DOOR_Z, PersistentDataType.INTEGER, 0) == lowerBlock.getZ()) {
                    return display;
                }
            }
        }
        return null;
    }

    // item_display spawn
    public void spawnDoorDisplay(Block lowerBlock, Door door) {
        if (getDisplayForBlock(lowerBlock) != null) return;

        int cmd = getModelData(lowerBlock.getType(), door.getHinge());
        if (cmd == -1) return;

        // edit ItemStack to set cmd
        ItemStack nugget = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = nugget.getItemMeta();
        meta.setCustomModelData(cmd);
        nugget.setItemMeta(meta);

        Vector offset = getOffset(door.getFacing(), door.getHinge());
        Location spawnLoc = lowerBlock.getLocation().add(0.5, 1.890, 0.5).add(offset);
        spawnLoc.setYaw(getBaseYaw(door.getFacing(), door.getHinge()));

        ItemDisplay display = (ItemDisplay) lowerBlock.getWorld().spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);

        // spawn item_display with our ItemStack, scale, rotation & position
        display.setItemStack(nugget);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        display.setTeleportDuration(4);
        float scale = 0.4375f;
        org.bukkit.util.Transformation transform = display.getTransformation();
        transform.getScale().set(scale, scale, scale);
        display.setTransformation(transform);

        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(DOOR_KEY, PersistentDataType.BYTE, (byte) 1);
        pdc.set(DOOR_X, PersistentDataType.INTEGER, lowerBlock.getX());
        pdc.set(DOOR_Y, PersistentDataType.INTEGER, lowerBlock.getY());
        pdc.set(DOOR_Z, PersistentDataType.INTEGER, lowerBlock.getZ());
        pdc.set(DOOR_STATE, PersistentDataType.BYTE, (byte) (door.isOpen() ? 1 : 0));
    }

    // consts, getting model data by door type, change only if you changed model_data in rp
    public int getModelData(Material material, Door.Hinge hinge) {
        int base = switch (material) {
            case OAK_DOOR -> 1;
            case SPRUCE_DOOR -> 3;
            case BIRCH_DOOR -> 5;
            case JUNGLE_DOOR -> 7;
            case ACACIA_DOOR -> 9;
            case DARK_OAK_DOOR -> 11;
            case CRIMSON_DOOR -> 13;
            case WARPED_DOOR -> 15;
            case IRON_DOOR -> 17;
            case CHERRY_DOOR -> 19;
            case MANGROVE_DOOR -> 21;
            case BAMBOO_DOOR -> 23;
            case COPPER_DOOR, WAXED_COPPER_DOOR -> 25;
            case EXPOSED_COPPER_DOOR, WAXED_EXPOSED_COPPER_DOOR -> 27;
            case OXIDIZED_COPPER_DOOR, WAXED_OXIDIZED_COPPER_DOOR -> 29;
            case WEATHERED_COPPER_DOOR, WAXED_WEATHERED_COPPER_DOOR -> 31;
            case PALE_OAK_DOOR -> 33;
            default -> -1;
        };
        if (base == -1) return -1;
        return hinge == Door.Hinge.RIGHT ? base + 1 : base;
    }

    // consts! change it only if there is issues with doors positioning
    public Vector getOffset(BlockFace facing, Door.Hinge hinge) {
        double x = 0, z = 0;
        if (facing == BlockFace.NORTH) {
            x = (hinge == Door.Hinge.LEFT) ? -0.4060 : 0.4060;
            z = 0.4060;
        } else if (facing == BlockFace.SOUTH) {
            x = (hinge == Door.Hinge.LEFT) ? 0.4060 : -0.4060;
            z = -0.4060;
        } else if (facing == BlockFace.EAST) {
            x = -0.4060;
            z = (hinge == Door.Hinge.LEFT) ? -0.4060 : 0.4060;
        } else if (facing == BlockFace.WEST) {
            x = 0.4060;
            z = (hinge == Door.Hinge.LEFT) ? 0.4060 : -0.4060;
        }
        return new Vector(x, 0, z);
    }

    // consts too! change it only if there is issues with doors positioning
    public float getBaseYaw(BlockFace facing, Door.Hinge hinge) {
        return switch (facing) {
            case NORTH -> 0f;
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    // checking door facing to prevent issues with double-open
    public BlockFace getPartnerFace(BlockFace facing, Door.Hinge hinge) {
        return switch (facing) {
            case NORTH -> hinge == Door.Hinge.LEFT ? BlockFace.EAST : BlockFace.WEST;
            case SOUTH -> hinge == Door.Hinge.LEFT ? BlockFace.WEST : BlockFace.EAST;
            case EAST -> hinge == Door.Hinge.LEFT ? BlockFace.SOUTH : BlockFace.NORTH;
            case WEST -> hinge == Door.Hinge.LEFT ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> null;
        };
    }
}