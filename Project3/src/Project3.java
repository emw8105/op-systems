import java.io.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

interface AllocationMethod {
    void displayFile(DiskDrive disk, String fileName);

    void displayFileTable(DiskDrive disk);

    default void displayBitMap(DiskDrive disk) {
        byte[] bitmapBlock = disk.readBlock(1);

        System.out.println("Free Space Bitmap:");

        // each byte represents 8 blocks in the bitmap
       for (int i = 0; i < 256; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);

            int bit = (bitmapBlock[byteIndex] >> bitIndex) & 1;
            System.out.print(bit);

            // formatting to keep rows to 32 bits
            if ((i + 1) % 32 == 0) {
                System.out.println();
            }
        }
        System.out.println();
    }

    default void displayDiskBlock(DiskDrive disk, int blockNum) {
        if (blockNum >= 0 && blockNum < 256) {
            byte[] blockData = disk.readBlock(blockNum);

            System.out.println("Disk Block " + blockNum + " Contents:");
            for (int i = 0; i < blockData.length; i++) {
                System.out.print(blockData[i] + " ");
                if ((i + 1) % 16 == 0) {
                    System.out.println();
                }
            }
            System.out.println();
        }
        else {
            System.out.println("Not a valid block number");
        }
    }

    byte[] readMemory(DiskDrive disk, String fileName); 

    int allocateMemory(DiskDrive disk, String fileName, byte[] data);

    void deleteFile(DiskDrive disk, String fileName);
}

class ContiguousAllocation implements AllocationMethod {
    private static final int FILE_NAME_LEN = 8;
    private static final int STARTING_BLOCK_LEN = 4;
    private static final int NUM_BLOCKS_LEN = 4;
    private static final int FILE_TABLE_ENTRY_LEN = FILE_NAME_LEN + STARTING_BLOCK_LEN + NUM_BLOCKS_LEN;
    // 16 bytes to an entry in the file table

    public void displayFile(DiskDrive disk, String fileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate through the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(fileName)) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int numBlocks = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                // display the contents of the file by reading the data from contiguous blocks
                StringBuilder fileContent = new StringBuilder();
                for (int j = 0; j < numBlocks; j++) {
                    int currentBlock = startingBlock + j;
                    byte[] blockData = disk.readBlock(currentBlock);

                    String blockContent = new String(blockData, StandardCharsets.UTF_8); // convert the bytes to string
                    fileContent.append(blockContent);
                }

                // print file contents
                System.out.println("Contents of File " + fileName + ":");
                System.out.println(fileContent.toString());
                return;
            }
        }

        // if the method reaches this point, the file was not found
        System.out.println("File not found: " + fileName);
    }

    public void displayFileTable(DiskDrive disk) {
        byte[] fileTableBlock = disk.readBlock(0);

        System.out.println("File Table:\n");
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = new byte[FILE_TABLE_ENTRY_LEN];
            System.arraycopy(fileTableBlock, i, entry, 0, FILE_TABLE_ENTRY_LEN);
            
            // display file table entry details and skip empty entries
            String fileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8);
            if (!Arrays.equals(entry, new byte[FILE_TABLE_ENTRY_LEN])) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int numBlocks = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                System.out.printf(fileName + "\t" + startingBlock + "\t" + numBlocks + "\n");
            }
        }
    }

    public int allocateMemory(DiskDrive disk, String fileName, byte[] fileData) {
        try {
            byte[] bitmapBlock = disk.readBlock(1);
            int numBlocks = (fileData.length + 511) / 512;

            if(numBlocks > 10) {
                return 0; // file is too big, maximum size is 10 blocks
            }

            // allocate contiguous blocks for the file in the simulation
            int startingBlock = findFreeBlocks(disk, numBlocks);
            if (startingBlock != -1) {
                bitmapBlock[0] |= 0b11000000; // allocation for block 0 and 1

                createFileTableEntry(disk, fileName, startingBlock, numBlocks);

                // loop to write file data to contiguous blocks
                for (int i = 0; i < numBlocks; i++) {
                    int currentBlock = startingBlock + i;
                    int startIdx = i * 512;
                    int endIdx = Math.min(startIdx + 512, fileData.length);

                    // extract the section of file data corresponding to the current block and write it to the disk
                    byte[] blockData = Arrays.copyOfRange(fileData, startIdx, endIdx);
                    disk.writeBlock(currentBlock, blockData);
                }

                // update the bitmap to include the allocation for file data
                disk.writeBlock(1, bitmapBlock);
                return 1; // successfully copied the file
            } else {
                return -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error reading file from the real system.");
            return -1;
        }
    }


    private int findFreeBlocks(DiskDrive disk, int numBlocks) {
        byte[] bitmapBlock = disk.readBlock(1);

        // scan the bitmap to find a sequence of contiguous free blocks
        for (int i = 2; i < disk.disk.length - numBlocks + 2; i++) {
            boolean isFree = true; // assume there is free space

            // check if the consecutive blocks are free
            for (int j = 0; j < numBlocks; j++) {
                if ((bitmapBlock[(i + j) / 8] & (1 << (7 - ((i + j) % 8)))) != 0) {
                    isFree = false; // if the j-th bit is set, the block is not free
                    break;
                }
            }

            if (isFree) {
                for (int j = 0; j < numBlocks; j++) {
                    bitmapBlock[(i + j) / 8] |= (1 << (7 - ((i + j) % 8))); // now mark the blocks for allocation
                }

                disk.writeBlock(1, bitmapBlock); // update the bitmap to include the allocation

                return i; // return the starting block index
            }
        }

        return -1; // there does not exist a free sequence of contiguous blocks large enough to store the file
    }


    private void createFileTableEntry(DiskDrive disk, String fileName, int startingBlock, int numBlocks) {
        byte[] entry = new byte[FILE_TABLE_ENTRY_LEN];

        // copy the file name to the entry 
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, entry, 0, Math.min(FILE_NAME_LEN, nameBytes.length));

        // convert starting block and numBlocks to byte arrays and copy them to entry
        byte[] startingBlockBytes = ByteBuffer.allocate(STARTING_BLOCK_LEN).putInt(startingBlock).array();
        byte[] numBlocksBytes = ByteBuffer.allocate(NUM_BLOCKS_LEN).putInt(numBlocks).array();
        System.arraycopy(startingBlockBytes, 0, entry, FILE_NAME_LEN, STARTING_BLOCK_LEN);
        System.arraycopy(numBlocksBytes, 0, entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN);

        int entryIndex = findFreeFileTableEntryIndex(disk); // find location in the table to insert

        // write the entry into the file table block
        byte[] fileTableBlock = disk.readBlock(0);
        System.arraycopy(entry, 0, fileTableBlock, entryIndex, entry.length);
        disk.writeBlock(0, fileTableBlock);
    }

    private int findFreeFileTableEntryIndex(DiskDrive disk) {
        byte[] fileTableBlock = disk.readBlock(0);

        // find the next empty space available in the table
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            boolean isFree = true;
            for (int j = 0; j < FILE_TABLE_ENTRY_LEN; j++) {
                if (fileTableBlock[i + j] != 0) {
                    isFree = false;
                    break;
                }
            }
            if (isFree) {
                return i;
            }
        }

        // if no free entry is found, return -1
        return -1;
    }

    public byte[] readMemory(DiskDrive disk, String simFileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(simFileName)) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int numBlocks = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                // read file data from contiguous blocks from start block thru the length
                ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
                for (int j = 0; j < numBlocks; j++) {
                    int currentBlock = startingBlock + j;
                    byte[] blockData = disk.readBlock(currentBlock);

                    try {
                        fileContent.write(blockData);
                    }
                    catch(IOException e) {
                        e.printStackTrace();
                    }
                }
                return fileContent.toByteArray();
            }
        }

        // file not found if we get to this point
        System.out.println("File not found: " + simFileName);
        return null;
    }

    public void deleteFile(DiskDrive disk, String fileName) {
        // use lazy deletion to remove file allocation so other files can be allocated

        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(fileName)) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int numBlocks = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                // mark the blocks as free in the bitmap (lazy deletion)
                byte[] bitmapBlock = disk.readBlock(1);
                for (int j = 0; j < numBlocks; j++) {
                    int currentBlock = startingBlock + j;
                    bitmapBlock[currentBlock / 8] &= ~(1 << (7 - (currentBlock % 8))); // Clear the bit
                }

                // update the bitmap with the deallocation changes
                disk.writeBlock(1, bitmapBlock);

                // delete the file table entry
                Arrays.fill(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN, (byte) 0);
                disk.writeBlock(0, fileTableBlock);

                System.out.println("File " + fileName + " deleted");
                return;
            }
        }

        // if the method reaches this point, the file was not found
        System.out.println("File not found: " + fileName);
    }
}



class ChainedAllocation implements AllocationMethod {
    private static final int FILE_NAME_LEN = 8;
    private static final int STARTING_BLOCK_LEN = 4;
    private static final int NUM_BLOCKS_LEN = 4;
    private static final int FILE_TABLE_ENTRY_LEN = FILE_NAME_LEN + STARTING_BLOCK_LEN + NUM_BLOCKS_LEN;
    // 16 bytes to an entry in the file table

    public void displayFile(DiskDrive disk, String fileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(fileName)) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int fileLength = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                // display the contents of the file by following the chain of blocks
                StringBuilder fileContent = new StringBuilder();
                int currentBlock = startingBlock;
                for (int j = 0; j < fileLength; j++) {
                    byte[] blockData = disk.readBlock(currentBlock);

                    // convert the bytes to string
                    // String blockContent = new String(blockData, StandardCharsets.UTF_8);
                    // fileContent.append(blockContent);
                    for(int h = 0; h < 508; h++) {
                        fileContent.append((char)(blockData[h] & 0xFF));
                    }
                    
                    currentBlock = ByteBuffer.wrap(blockData, 508, 4).getInt(); // find next block in the chain from last 4 bytes
                }

                // print the contents of the file
                System.out.println("Contents of File " + fileName + ":");
                System.out.println(fileContent.toString());
                return;
            }
        }

        // if the method reaches this point, the file was not found
        System.out.println("File not found: " + fileName);
    }

    public void displayFileTable(DiskDrive disk) {
        // get file table block and loop through the byte sections/entries
        byte[] fileTableBlock = disk.readBlock(0);

        System.out.println("File Table:\n");
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = new byte[FILE_TABLE_ENTRY_LEN];
            System.arraycopy(fileTableBlock, i, entry, 0, FILE_TABLE_ENTRY_LEN);
            
            // display file table entry details and skip empty entries
            String fileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8);
            if (!Arrays.equals(entry, new byte[FILE_TABLE_ENTRY_LEN])) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int numBlocks = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                System.out.printf(fileName + "\t" + startingBlock + "\t" + numBlocks + "\n");
            }
        }
    }

    public int allocateMemory(DiskDrive disk, String fileName, byte[] fileData) {
        int numBlocks = (fileData.length + 508) / 509;

        if(numBlocks > 10) {
            return 0; // file is too big, maximum size is 10 blocks
        }

        int[] availableBlockIndices = findAvailableBlocks(disk, numBlocks); // find available blocks

        // allocate file data to the available blocks
        if (availableBlockIndices != null) {
            allocateFileData(disk, availableBlockIndices, fileData);
            createFileTableEntry(disk, fileName, availableBlockIndices[0], numBlocks);
            return 1; // successfully allocated the file
        } else {
            return -1; // error or not enough free blocks
        }
    }

    private void allocateFileData(DiskDrive disk, int[] availableBlockIndices, byte[] fileData) {
        int remainingBytes = fileData.length;

        // write file data to available blocks
        for (int i = 0; i < availableBlockIndices.length; i++) { // loop through all blocks except the last one
            int blockIndex = availableBlockIndices[i];

            // calculate the block size and copy file data to the block
            int blockSize = Math.min(remainingBytes, 508);
            byte[] blockData = new byte[512];
            System.arraycopy(fileData, fileData.length - remainingBytes, blockData, 0, blockSize);

            byte[] nextBlockBytes = new byte[4];
            if (i < availableBlockIndices.length - 1) { // check if next block index is valid and append the next block index to the existing block data
                System.arraycopy(ByteBuffer.allocate(4).putInt(availableBlockIndices[i + 1]).array(), 0, nextBlockBytes, 0, 4);
            } else { // set next block index to 0 for last block
                System.arraycopy(ByteBuffer.allocate(4).putInt(0).array(), 0, nextBlockBytes, 0, 4);
            }
            System.arraycopy(nextBlockBytes, 0, blockData, 508, 4);

            // write the modified block data to the disk
            disk.writeBlock(blockIndex, blockData);

            remainingBytes -= blockSize;
        }
    }

    private int[] findAvailableBlocks(DiskDrive disk, int numBlocks) {
        byte[] bitmapBlock = disk.readBlock(1);
        int availableBlockCount = 0;

        // create an array to store available block indices
        int[] availableBlockIndices = new int[numBlocks];
        bitmapBlock[0] |= 0b11000000; // allocate file table and bitmap blocks on the bitmap

        // iterate thru the bitmap and find available blocks
        for (int i = 2; i < bitmapBlock.length * 8; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);

            int bit = (bitmapBlock[byteIndex] >> bitIndex) & 1;

            if (bit == 0) {
                // this block is free
                availableBlockIndices[availableBlockCount] = i;
                availableBlockCount++;

                int currentByte = bitmapBlock[byteIndex];
                int currentBit = 1 << bitIndex;
                bitmapBlock[byteIndex] = (byte) (currentByte | currentBit);

                if(availableBlockCount >= numBlocks) {
                    break;
                }
            }
        }

        // check if enough free blocks were found
        if (availableBlockCount < numBlocks) {
            return null; // not enough free blocks
        }
        disk.writeBlock(1, bitmapBlock);
        return availableBlockIndices;
    }
    

    private void createFileTableEntry(DiskDrive disk, String fileName, int startingBlock, int numBlocks) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the first empty entry
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);
            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();

            // check if the entry is empty
            if (entryFileName.isEmpty()) {
                // copy the file name to the entry
                byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
                System.arraycopy(nameBytes, 0, entry, 0, Math.min(FILE_NAME_LEN, nameBytes.length));

                // convert starting block and numBlocks to byte arrays and then copy them to the entry
                byte[] startingBlockBytes = ByteBuffer.allocate(STARTING_BLOCK_LEN).putInt(startingBlock).array();
                byte[] numBlocksBytes = ByteBuffer.allocate(NUM_BLOCKS_LEN).putInt(numBlocks).array();
                System.arraycopy(startingBlockBytes, 0, entry, FILE_NAME_LEN, STARTING_BLOCK_LEN);
                System.arraycopy(numBlocksBytes, 0, entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN);

                // write the entry back to the file table block
                System.arraycopy(entry, 0, fileTableBlock, i, entry.length);
                disk.writeBlock(0, fileTableBlock);

                return;
            }
        }

        System.out.println("Error: No empty entry found in the file table to add the new file");
    }

    public byte[] readMemory(DiskDrive disk, String simFileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(simFileName)) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                int numBlocks = ByteBuffer.wrap(entry, FILE_NAME_LEN + STARTING_BLOCK_LEN, NUM_BLOCKS_LEN).getInt();

                // read file data from contiguous blocks from start block thru the length
                ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
                int currentBlock = startingBlock;
                for (int j = 0; j < numBlocks; j++) {
                    byte[] blockData = disk.readBlock(currentBlock);

                    for (int h = 0; h < 508; h++) {
                        // convert each character to a byte by masking with chars
                        byte[] charBytes = Character.toString((char) (blockData[h] & 0xFF)).getBytes(StandardCharsets.UTF_8);
                        try {
                            fileContent.write(charBytes);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // get the next block in the chain
                    currentBlock = ByteBuffer.wrap(blockData, 508, 4).getInt();
                }
                return fileContent.toByteArray();
            }
        }

        // file not found if we get to this point
        try {
            throw new FileNotFoundException("File not found: " + simFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteFile(DiskDrive disk, String fileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);
            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();

            // check if the entry matches the file to be deleted
            if (entryFileName.equals(fileName)) {
                // deallocate blocks in the bitmap
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, STARTING_BLOCK_LEN).getInt();
                deallocateChainedBlocks(disk, startingBlock);

                // clear the file table entry
                Arrays.fill(entry, (byte) 0);
                System.arraycopy(entry, 0, fileTableBlock, i, entry.length);
                disk.writeBlock(0, fileTableBlock);

                System.out.println("File " + fileName + " deleted from simulation");
                return;
            }
        }

        // file not found if we get to this point
        System.out.println("Error: File not found - " + fileName);
    }

    private void deallocateChainedBlocks(DiskDrive disk, int startingBlock) {
        byte[] bitmapBlock = disk.readBlock(1);

        // deallocate blocks by marking them as free in the bitmap
        while (startingBlock != 0) {
            int byteIndex = startingBlock / 8;
            int bitIndex = 7 - (startingBlock % 8);
            bitmapBlock[byteIndex] &= ~(1 << bitIndex); // clear the bit to mark the block as free

            // read the next block in the chain
            byte[] nextBlockData = disk.readBlock(startingBlock);
            startingBlock = ByteBuffer.wrap(nextBlockData, 508, 4).getInt();
        }

        disk.writeBlock(1, bitmapBlock);
    }
}



class IndexedAllocation implements AllocationMethod {
    private static final int FILE_NAME_LEN = 8;
    private static final int INDEX_BLOCK_LEN = 4;
    private static final int FILE_TABLE_ENTRY_LEN = FILE_NAME_LEN + INDEX_BLOCK_LEN;

    public void displayFile(DiskDrive disk, String fileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        boolean fileFound = false;
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(fileName)) {
                fileFound = true;
                int indexBlockNumber = ByteBuffer.wrap(entry, FILE_NAME_LEN, INDEX_BLOCK_LEN).getInt();

                // read the index block to get the data block indices
                byte[] indexBlockData = disk.readBlock(indexBlockNumber);
                int[] dataBlockIndices = new int[indexBlockData.length / 8];
                int[] blockLengths = new int[indexBlockData.length / 8];

                for (int j = 0; j < dataBlockIndices.length; j++) {
                    dataBlockIndices[j] = ByteBuffer.wrap(indexBlockData, j * 8, 4).getInt();
                    blockLengths[j] = ByteBuffer.wrap(indexBlockData, j * 8 + 4, 4).getInt();
                }

                // display the contents of the file from the data blocks
                StringBuilder fileContent = new StringBuilder();
                for (int k = 0; k < dataBlockIndices.length; k++) {
                    int dataBlockIndex = dataBlockIndices[k];
                    int blockLength = blockLengths[k];
                    byte[] blockData = disk.readBlock(dataBlockIndex);

                    // convert the bytes to string
                    String blockContent = new String(blockData, 0, blockLength, StandardCharsets.UTF_8);
                    fileContent.append(blockContent);
                }

                // print the contents of the file
                System.out.println("Contents of File " + fileName + ":");
                System.out.println(fileContent.toString());
            }
        }

        // file does not exist if we get to this point
        if(fileFound == false) {
            System.out.println("No file with the name \'" + fileName + "\' in the system");
        }
    }

    public void displayFileTable(DiskDrive disk) { // similar to the others but the entries are only 12 bytes (length not stored)
        byte[] fileTableBlock = disk.readBlock(0);

        System.out.println("File Table:\n");
        for (int i = 0; i < fileTableBlock.length-8; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = new byte[FILE_TABLE_ENTRY_LEN];
            System.arraycopy(fileTableBlock, i, entry, 0, FILE_TABLE_ENTRY_LEN);
            
            // display file table entry details and skip empty entries
            String fileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8);
            if (!Arrays.equals(entry, new byte[FILE_TABLE_ENTRY_LEN])) {
                int startingBlock = ByteBuffer.wrap(entry, FILE_NAME_LEN, INDEX_BLOCK_LEN).getInt();

                System.out.printf(fileName + "\t" + startingBlock + "\n");
            }
        }
    }

    public int allocateMemory(DiskDrive disk, String fileName, byte[] fileData) {
        try {
            // calculate the number of data blocks needed for the file data
            int numBlocks = (fileData.length + 511) / 512;
            numBlocks++; // allocate one extra to be the index block
            if(numBlocks > 10) {
                return 0; // file is too big, maximum size is 10 blocks
            }

            int[] dataBlockIndices = findAvailableBlocks(disk, numBlocks);

            allocateIndexBlocks(disk, dataBlockIndices, fileData); // allocate blocks and data

            createFileTableEntry(disk, fileName, dataBlockIndices[0]); // create file table entry

            return 1; // successfully allocated the file
        } catch(Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private int[] findAvailableBlocks(DiskDrive disk, int numBlocks) {
        byte[] bitmapBlock = disk.readBlock(1);
        int availableBlockCount = 0;

        // create an array to store available block indices to be stored in the index block
        int[] availableBlockIndices = new int[numBlocks];
        bitmapBlock[0] |= 0b11000000; // allocate the file table and bitmap

        // iterate thru the bitmap and find available blocks
        for (int i = 2; i < bitmapBlock.length * 8; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);

            int bit = (bitmapBlock[byteIndex] >> bitIndex) & 1;

            if (bit == 0) {
                // this block is free
                availableBlockIndices[availableBlockCount] = i;
                availableBlockCount++;

                int currentByte = bitmapBlock[byteIndex];
                int currentBit = 1 << bitIndex;
                bitmapBlock[byteIndex] = (byte) (currentByte | currentBit);

                if(availableBlockCount >= numBlocks) {
                    break;
                }
            }
        }

        // check if enough free blocks were found
        if (availableBlockCount < numBlocks) {
            return null; // not enough free blocks
        }
        disk.writeBlock(1, bitmapBlock);
        return availableBlockIndices;
    }

    private void allocateIndexBlocks(DiskDrive disk, int[] dataBlockIndices, byte[] fileData) {
        byte[] indexBlockData = new byte[512];

        // store the data block indices and lengths in the index block but skip the index block itself
        for (int i = 1; i < dataBlockIndices.length; i++) {
            ByteBuffer.wrap(indexBlockData, (i - 1) * 8, 4).putInt(dataBlockIndices[i]);
            ByteBuffer.wrap(indexBlockData, (i - 1) * 8 + 4, 4).putInt(calculateBlockLength(fileData, (i - 1) * 512));
        }

        // write the index block to the first allocated block
        disk.writeBlock(dataBlockIndices[0], indexBlockData);

        // now we write the file data to the blocks
        for (int i = 1; i < dataBlockIndices.length; i++) { // start from index 1 for data blocks, skip index block
            int dataBlockIndex = dataBlockIndices[i];

            int startIdx = (i - 1) * 512;
            int endIdx = Math.min(startIdx + 512, fileData.length);
            byte[] blockData = Arrays.copyOfRange(fileData, startIdx, endIdx);

            // write the block data to the disk
            disk.writeBlock(dataBlockIndex, blockData);
        }
    }

    private int calculateBlockLength(byte[] fileData, int startIdx) {
        int endIdx = Math.min(startIdx + 512, fileData.length);
        return endIdx - startIdx;
    }

    private void createFileTableEntry(DiskDrive disk, String fileName, int startingBlock) {
        byte[] entry = new byte[FILE_TABLE_ENTRY_LEN];

        // copy the file name to the entry
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, entry, 0, Math.min(FILE_NAME_LEN, nameBytes.length));

        // convert starting block to byte arrays and copy it to the entry
        byte[] startingBlockBytes = ByteBuffer.allocate(INDEX_BLOCK_LEN).putInt(startingBlock).array();
        System.arraycopy(startingBlockBytes, 0, entry, FILE_NAME_LEN, INDEX_BLOCK_LEN);

        int entryIndex = findFreeFileTableEntryIndex(disk); // find next available file table entry location

        if (entryIndex != -1) {
            // write the entry into the file table block
            byte[] fileTableBlock = disk.readBlock(0);
            System.arraycopy(entry, 0, fileTableBlock, entryIndex, entry.length);
            disk.writeBlock(0, fileTableBlock);
        } else {
            System.out.println("File table is full");
        }
        
    }

    private int findFreeFileTableEntryIndex(DiskDrive disk) {
        byte[] fileTableBlock = disk.readBlock(0);

        // find the next empty space available in the table
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            boolean isFree = true;
            for (int j = 0; j < FILE_TABLE_ENTRY_LEN; j++) {
                if (fileTableBlock[i + j] != 0) {
                    isFree = false;
                    break;
                }
            }
            if (isFree) {
                return i;
            }
        }

        // If no free entry is found, return -1
        return -1;
    }

    public byte[] readMemory(DiskDrive disk, String simFileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(simFileName)) {
                int indexBlockNumber = ByteBuffer.wrap(entry, FILE_NAME_LEN, INDEX_BLOCK_LEN).getInt();

                // read the index block to get the data block indices and lengths
                byte[] indexBlockData = disk.readBlock(indexBlockNumber);
                int[] dataBlockIndices = new int[indexBlockData.length / 8];
                int[] blockLengths = new int[indexBlockData.length / 8];

                for (int j = 0; j < dataBlockIndices.length; j++) {
                    dataBlockIndices[j] = ByteBuffer.wrap(indexBlockData, j * 8, 4).getInt();
                    blockLengths[j] = ByteBuffer.wrap(indexBlockData, j * 8 + 4, 4).getInt();
                }

                // read file data from indexed blocks
                ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
                for (int k = 0; k < dataBlockIndices.length; k++) {
                    int dataBlockIndex = dataBlockIndices[k];
                    int blockLength = blockLengths[k];
                    
                    byte[] fullBlockData = disk.readBlock(dataBlockIndex); // read the whole block into one array

                    byte[] blockData = Arrays.copyOfRange(fullBlockData, 0, blockLength); // get respective data based on the block length

                    try {
                        fileContent.write(blockData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return fileContent.toByteArray();
            }
        }

        // file not found if we get to this point
        System.out.println("File not found: " + simFileName);
        return null;
    }

    public void deleteFile(DiskDrive disk, String simFileName) {
        byte[] fileTableBlock = disk.readBlock(0);

        // iterate thru the file table to find the entry corresponding to the given file name
        int emptyEntryPosition = -1;
        for (int i = 0; i < fileTableBlock.length; i += FILE_TABLE_ENTRY_LEN) {
            byte[] entry = Arrays.copyOfRange(fileTableBlock, i, i + FILE_TABLE_ENTRY_LEN);

            String entryFileName = new String(entry, 0, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
            if (entryFileName.equals(simFileName)) {
                emptyEntryPosition = i;
                break;
            }
        }

        if (emptyEntryPosition == -1) {
            // file not found if we get to this point
            System.out.println("File not found: " + simFileName);
            return;
        }

        // create a new file table block with the deleted entry removed
        byte[] updatedFileTableBlock = new byte[fileTableBlock.length];
        System.arraycopy(fileTableBlock, 0, updatedFileTableBlock, 0, emptyEntryPosition);
        System.arraycopy(fileTableBlock, emptyEntryPosition + FILE_TABLE_ENTRY_LEN, updatedFileTableBlock, emptyEntryPosition, fileTableBlock.length - emptyEntryPosition - FILE_TABLE_ENTRY_LEN);

        // delete the file's data blocks and index block
        String entryFileName = new String(fileTableBlock, emptyEntryPosition, FILE_NAME_LEN, StandardCharsets.UTF_8).trim();
        int indexBlockNumber = ByteBuffer.wrap(fileTableBlock, emptyEntryPosition + FILE_NAME_LEN, INDEX_BLOCK_LEN).getInt();
        deallocateBlocks(disk, indexBlockNumber);

        // write the updated file table block to the disk
        disk.writeBlock(0, updatedFileTableBlock);

        System.out.println("File " + entryFileName + " deleted");
    }

    private void deallocateBlocks(DiskDrive disk, int indexBlockNumber) {
        byte[] bitmapBlock = disk.readBlock(1);

        // read the index block
        byte[] indexBlockData = disk.readBlock(indexBlockNumber);

        // create a temporary copy of the bitmap block
        byte[] updatedBitmapBlock = Arrays.copyOf(bitmapBlock, bitmapBlock.length);

        // deallocate the data blocks listed in the index block
        for (int i = 0; i < indexBlockData.length / 8; i++) {
            int dataBlockIndex = ByteBuffer.wrap(indexBlockData, i * 8, 4).getInt();

            // mark the data block as free in the bitmap
            int byteIndex = dataBlockIndex / 8;
            int bitIndex = 7 - (dataBlockIndex % 8);
            updatedBitmapBlock[byteIndex] &= ~(1 << bitIndex); // Clear the bit to mark the block as free
        }

        // deallocate the index block itself
        int byteIndex = indexBlockNumber / 8;
        int bitIndex = 7 - (indexBlockNumber % 8);
        updatedBitmapBlock[byteIndex] &= ~(1 << bitIndex);

        // update the bitmap to reflect the deallocation
        disk.writeBlock(1, updatedBitmapBlock);
    }
}

// handles methods deadling with allocating files
// differentiated based on the type of file allocation method
class FileSystem {

    private AllocationMethod allocationMethod;
    private DiskDrive disk;

    public FileSystem(String allocationInput) {
        this.disk = new DiskDrive();

        // determine which allocation method to use based on user input
        switch (allocationInput.toLowerCase()) {
            case "contiguous":
                this.allocationMethod = new ContiguousAllocation();
                break;
            case "chained":
                this.allocationMethod = new ChainedAllocation();
                break;
            case "indexed":
                this.allocationMethod = new IndexedAllocation();
                break;
            default:
                System.out.println("Invalid allocation method. Choose from: contiguous, chained, indexed");
                System.exit(1);
        }
    }

    // functions abstract the allocation method by utilizing the interface
    public void displayFile(String fileName) {
        allocationMethod.displayFile(disk, fileName);
    }

    public void displayFileTable() {
        allocationMethod.displayFileTable(disk);
    }

    public void displayBitMap() {
        allocationMethod.displayBitMap(disk);
    }
    public void displayDiskBlock(int blockNum) {
        allocationMethod.displayDiskBlock(disk, blockNum);
    }

    public void copyFileToRealSystem(String simFileName, String outputName) {
        try {
            // get the file data from the simulation
            byte[] fileData = allocationMethod.readMemory(disk, simFileName);

            // write the file data to the real file system as a text file
            try (FileOutputStream fileOutputStream = new FileOutputStream(outputName);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
                outputStreamWriter.write(new String(fileData, StandardCharsets.UTF_8));
            }

            System.out.println("File " + simFileName + " copied to " + outputName);
        } catch (IOException e) {
            System.out.println("Error copying the file: " + e.getMessage());
        }
    }

    public void copyFileToSimulation(String realFileName, String simName) {
        File inputFile = new File(realFileName);
        int result = 0;
        try {
            byte[] fileData = Files.readAllBytes(inputFile.toPath());
            result = allocationMethod.allocateMemory(disk, simName, fileData);
        } catch(IOException e) {
            System.out.println(e);
        }
        
        if(result == 1) {
            System.out.println("File " + realFileName + " copied");
        }
        else if (result == 0) {
            System.out.println("File cannot take up more than 10 blocks, please choose a smaller file");
        }
        else {
            System.out.println("Error copying the file");
        }
    }

    public void deleteFile(String fileName) {
        allocationMethod.deleteFile(disk, fileName);
    }
}

class DiskDrive {
    public byte disk[][];

    public DiskDrive() {
        disk = new byte[256][512];
        // first block is for the file allocation table
        // second block is a bitmap for free space management
        // remaining blocks hold data for files
    }

    public byte[] readBlock(int blockNumber) {
        return disk[blockNumber];
    }

    public void writeBlock(int blockNumber, byte[] data) {
        disk[blockNumber] = data;
    }

// File names should be up to 8 characters.  Names should only have lowercase letters.  File extensions are not supported.  

// The maximum file size should be 10 blocks.  All files reside at the root level, and subdirectories are not supported.

// The format of the file allocation table should be as shown in the slides, with a fixed size for each field.

// Problems such as not having enough space should result in an error message to the user.

}

class Main {
    public static void main(String args[]) {

        UserInterface UI = new UserInterface();
        String allocationInput = "";
        try {
            allocationInput = args[0]; // allocation method desired is entered thru command line
        } catch (IndexOutOfBoundsException e) {
            System.out.println("You must enter a file allocation method, please choose from: contiguous, chained, indexed");
            System.exit(1);
        }

        FileSystem fs = new FileSystem(allocationInput);

        while(true) {
            UI.menu(fs);
        }
    }
}

class UserInterface {
    public void menu(FileSystem fs) {
        try (Scanner input = new Scanner(System.in)) {
            while (true) {
                System.out.println("1) Display a file\n2) Display the file table\n3) Display the free space bitmap"
                        + "\n4) Display a disk block\n5) Copy a file from the simulation to a file on the real system"
                        + "\n6) Copy a file from the real system to a file on the simulation\n7) Delete a file\n8) Exit");

                int menuChoice = getMenuChoice(input);

                switch (menuChoice) {
                    case 1:
                        // display the contents of a given file in the simulation
                        System.out.print("Enter the name of the file to display: ");
                        String fileName = input.next();
                        fs.displayFile(fileName);
                        break;
                    case 2:
                        // print out the file table stored in the first block
                        fs.displayFileTable();
                        break;
                    case 3:
                        // print out the bit map stored in the second block
                        fs.displayBitMap();
                        break;
                    case 4:
                        // display a specific disk block (print out the bytes in a given block)
                        System.out.print("Enter the block number to display: ");
                        int blockNumber = input.nextInt();
                        fs.displayDiskBlock(blockNumber);
                        break;
                    case 5:
                        // copy a file from the simulation to a file on the real system
                        System.out.print("Enter the name of the file to copy from simulation to real system: ");
                        String simFileName = input.next();
                        System.out.print("Enter the name of the file in the real system to copy to: ");
                        String outputPath = input.next();

                        fs.copyFileToRealSystem(simFileName, outputPath);
                        break;
                    case 6:
                        // copy a file from the real system to a file on the simulation
                        System.out.print("Copy from: ");
                        String realFileName = input.next();
                        System.out.print("Copy to: ");
                        simFileName = input.next();

                        fs.copyFileToSimulation(realFileName, simFileName);
                        break;
                    case 7:
                        // Delete a file (update the file table and bitmap as well)
                        System.out.print("Enter the name of the file to delete: ");
                        String deleteFileName = input.next();
                        fs.deleteFile(deleteFileName);
                        break;
                    case 8:
                        System.out.println("Exiting");
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 8.");
                        break;
                }
            }
        }
    }

    private int getMenuChoice(Scanner input) {
        int choice = -1;
        while (choice < 1 || choice > 8) {
            try {
                System.out.print("Choice: ");
                choice = Integer.parseInt(input.next());
                if (choice < 1 || choice > 8) {
                    System.out.println("Invalid choice. Please enter a number between 1 and 8.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
        return choice;
    }
}