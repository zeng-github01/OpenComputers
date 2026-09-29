package li.cil.oc.api.detail;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public interface SaveHandlerAPI {
    /**
     * Loads previously saved out-of-band binary data associated with the given NBT tag.
     * <br>
     * This retrieves the storage location metadata written into the specified NBT tag
     * compound (which was populated when calling {@link #scheduleSave(World, BlockPos, NBTTagCompound, String, byte[])})
     * and loads the corresponding stored data from disk.
     *
     * @param nbt  the tag compound containing the save metadata.
     * @param name the unique identifier name of the data segment.
     * @return the loaded byte array, or {@code null} if no such data exists.
     */
    byte[] load(NBTTagCompound nbt, String name);

    /**
     * Loads previously saved out-of-band binary data from the specified dimension and chunk coordinates.
     *
     * @param dimension the dimension ID where the data is stored.
     * @param chunk     the chunk coordinates where the data is stored.
     * @param name      the unique identifier name of the data segment.
     * @return the loaded byte array, or {@code null} if no such data exists.
     */
    byte[] load(int dimension, ChunkPos chunk, String name);

    /**
     * Schedules out-of-band binary data to be saved asynchronously for the specified dimension and chunk.
     * <br>
     * Data saved this way is written to disk separately from the world chunk NBT,
     * avoiding network packet overhead and Netty packet size limitations when syncing tile entities.
     *
     * @param dimension the dimension ID where the data should be saved.
     * @param chunk     the chunk coordinates where the data should be saved.
     * @param name      the unique identifier name of the data segment.
     * @param data      the raw binary data to be saved.
     */
    void scheduleSave(int dimension, ChunkPos chunk, String name, byte[] data);

    /**
     * Schedules out-of-band binary data to be saved asynchronously and updates the given NBT tag.
     * <br>
     * This method saves the binary data outside of the chunk NBT, while automatically writing
     * location and lookup metadata into the provided {@code nbt} compound. This allows the
     * data to be easily restored later using {@link #load(NBTTagCompound, String)}.
     * <br>
     * This is the recommended method for architectures and components to store large state
     * data (such as virtual machine memory dumps) safely without inflating network packets.
     *
     * @param world the world containing the host block.
     * @param pos   the position of the host block.
     * @param nbt   the tag compound to populate with retrieval metadata.
     * @param name  the unique identifier name of the data segment.
     * @param data  the raw binary data to be saved.
     */
    void scheduleSave(World world, BlockPos pos, NBTTagCompound nbt, String name, byte[] data);
}
