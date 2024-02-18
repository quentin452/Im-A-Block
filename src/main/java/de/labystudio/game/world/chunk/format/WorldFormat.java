package de.labystudio.game.world.chunk.format;

import de.labystudio.game.world.World;
import de.labystudio.game.world.chunk.Chunk;
import de.labystudio.game.world.chunk.ChunkSection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WorldFormat {

    private final World world;
    private final File regionDirectory;
    private final String name;

    public WorldFormat(World world, File worldDirectory) {
        this.world = world;
        this.name = worldDirectory.getName();
        this.regionDirectory = new File(worldDirectory, "region");
    }

    public RegionFormat getRegion(int x, int z) {
        File file = new File(this.regionDirectory, RegionFormat.getFileName(x, z));

        File parent = file.getParentFile();
        if (parent != null && !file.exists() && parent.mkdirs()) {
            System.out.println("Created new world directory \"" + this.name + "\"");
        }

        return new RegionFormat(file);
    }

    public void load(WorldLoadingProgress worldLoadingProgress) {
        if (!this.regionDirectory.exists()) {
            System.out.println("World file not found");
            return;
        }

        File[] files = this.regionDirectory.listFiles();
        if (files != null) {
            for (File regionFile : files) {
                // Skip old region files
                if (regionFile.getName().endsWith("~"))
                    continue;

                try (RegionFormat region = new RegionFormat(regionFile)) {
                    for (int x = 0; x < 32; x++) {
                        for (int z = 0; z < 32; z++) {
                            try (DataInputStream inputStream = region.getChunkDataInputStream(x, z)) {
                                int chunkX = (region.x << 5) + x;
                                int chunkZ = (region.z << 5) + z;

                                // Read chunk layers
                                ChunkFormat chunkFormat = new ChunkFormat(this.world, x, z).read(inputStream, chunkX, chunkZ);
                                if (!chunkFormat.isEmpty()) {
                                    ChunkSection[] layers = chunkFormat.getChunks();

                                    // Fill empty chunks with chunk objects
                                    for (int y = 0; y < 16; y++) {
                                        if (layers[y] == null) {
                                            layers[y] = new ChunkSection(this.world, chunkX, y, chunkZ);
                                        }
                                    }

                                    // Load chunk layers
                                    worldLoadingProgress.onLoad(chunkX, chunkZ, layers);
                                }
                            }
                        }
                    }
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public void saveChunks() throws IOException {
        List<Long> regionsToSave = getLongs();

        System.out.println("Start saving world in " + regionsToSave.size() + " region files.");

        for (Long regionId : regionsToSave) {
            // Extract two ints from long id
            int regionX = (int) (regionId >> 32);
            int regionZ = (int) (long) regionId;

            // Get region file
            try (RegionFormat region = this.getRegion(regionX, regionZ)) {
                // Relative offset
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {

                        // Absolute chunk coordinates
                        int chunkX = (region.x << 5) + x;
                        int chunkZ = (region.z << 5) + z;

                        if (this.world.isChunkLoaded(chunkX, chunkZ)) {
                            // Get chunk
                            Chunk chunk = this.world.getChunkAt(chunkX, chunkZ);

                            // Write
                            if (!chunk.isEmpty()) {
                                try (DataOutputStream outputStream = region.getChunkDataOutputStream(x, z)) {
                                    ChunkFormat.write(chunk, outputStream);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Long> getLongs() {
        List<Long> regionsToSave = new ArrayList<>();

        // Get all regions to save
        for (Chunk chunk : this.world.chunks.values()) {
            if (chunk.isEmpty())
                continue;

            // Get region coordinates of this chunk
            int regionX = chunk.getX() >> 5;
            int regionZ = chunk.getZ() >> 5;

            // Create an id of this region coordinate
            long regionId = ((long) regionX) << 32L | regionZ & 0xFFFFFFFFL;

            // Add to queue
            if (!regionsToSave.contains(regionId)) {
                regionsToSave.add(regionId);
            }
        }
        return regionsToSave;
    }

    public boolean exists() {
        return this.regionDirectory.exists();
    }
}
