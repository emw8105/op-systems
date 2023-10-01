// Evan Wright, emw200004
// CS4348.006 - Project 1

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Memory {
    public static void main(String args[]) {
        int[] mem = new int[2000]; // array representing main memory, 0-999 is user space, 1000-2000 is system space

        Scanner CPUReader = new Scanner(System.in); // scanner to receive instructions from CPU
        File inputFile = null; // initialize to null or else fileScan gets mad
        String fileName = CPUReader.nextLine();
        if (fileName != null) { // CPU should send filename, open file with that file name
            inputFile = new File(fileName);

            if (!inputFile.canRead()) {
                System.out.println("File does not exist");
                System.exit(0);
            }
        }

        // read the instructions from the file into the array
        try {
            Scanner fileScan = new Scanner(inputFile);
            int i = 0;
            while (fileScan.hasNext()) {
                if (fileScan.hasNextInt()) { // read in integers normally
                    mem[i] = fileScan.nextInt(); // insert into memory
                    i++;
                } else {
                    String line = fileScan.next();
                    if (line.charAt(0) == '.') { // check for dots to change address
                        i = Integer.parseInt(line.substring(1)); // set address to be number following the dot
                    } else { // ignore everything else, i.e. comments
                        fileScan.nextLine();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // enter loop to receive instructions from the CPU
        while (CPUReader.hasNextLine()) {
            String temp = CPUReader.nextLine(); // Read CPU line
            if (temp.isEmpty()) { // stop looping if receiving no instruction
                break;
            }

            String[] commands = temp.split("/"); // split input into parts based on dots

            int operation = Integer.parseInt(commands[0]); // read/write
            int address = Integer.parseInt(commands[1]); // 0-2000

            if (operation == 1) {
                System.out.println(mem[address]); // read from memory at given address and print back to CPU
            } else if (operation == 2) {
                mem[address] = Integer.parseInt(commands[2]); // write the data to memory at given address
            }
        }
    }
}