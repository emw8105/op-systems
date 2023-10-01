// Evan Wright, emw200004
// CS4348.006 - Project 1

import java.io.*;
import java.util.Random;
import java.util.Scanner;

public class CPU {

    public static int PC = 0, SP = 1000, IR = 0, AC = 0, X = 0, Y = 0, instrCount = 0, mode = 0, systemStack = 2000, userStack = 1000;
    static boolean interrupt = false;

    public static void main(String[] args) {
        if(args.length < 2) {
            System.out.print("Not enough input arguments, must be input file name and timer length");
            System.exit(1);
        }

        String fileName = args[0];
        int interruptTime = Integer.parseInt(args[1]);

        try
        {
            Process memProcess = Runtime.getRuntime().exec("java Memory");

            OutputStream os = memProcess.getOutputStream(); // set up output stream to send instructions to memory
            PrintWriter pipe = new PrintWriter(os);

            InputStream is = memProcess.getInputStream(); // set up input stream to read from memory
            Scanner memScan = new Scanner(is);

            pipe.printf(fileName + "\n"); // print the file name to memory for it to load file contents into the array
            pipe.flush();

            // enter fetch/execute loop
            while (true)
            {
                // check if it's time for an interrupt
                if(shouldInterrupt(interrupt, instrCount, interruptTime))
                {
                    interrupt = true;
                    int temp;
                    mode = 1;
                    temp = SP;
                    SP = systemStack; // switch to system stack
                    pushToStack(temp, pipe); // push user program values onto system stack
                    pushToStack(PC, pipe);
                    PC = 1000; // switch to system code
                }

                // retrieve instruction from memory
                int instruction = readMemory(PC, pipe, memScan);

                if (instruction == -1) {
                    break;
                }
                execute(instruction, pipe, memScan);
            }
        }
        catch (IOException e)
        {
           e.printStackTrace();
        }

    }

    private static void execute(int instruction, PrintWriter pipe, Scanner memScan) //main instruction setup
    {
        IR = instruction; // put instruction into IR register
        int tempAddr; // used to simplify instructions with multiple PC movements
        //System.out.println("Attempting to execute instruction:" + IR);

        switch(IR) {
            case 1: //Load the following value into AC
                PC++;
                AC = readMemory(PC, pipe, memScan);
                countInc();
                PC++;
                break;

            case 2: //Load the value at the following address into AC
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                AC = readMemory(tempAddr, pipe, memScan);
                countInc();
                PC++;
                break;

            case 3: //IR 2 but with one extra address (address --> address --> AC)
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                tempAddr = readMemory(tempAddr, pipe, memScan);
                AC = readMemory(tempAddr, pipe, memScan);
                countInc();
                PC++;
                break;


            case 4: //Load value at (following address + X) into AC
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                AC = readMemory(tempAddr + X, pipe, memScan);
                countInc();
                PC++;
                break;

            case 5: //Load value at (following address + Y) into AC
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                AC = readMemory(tempAddr + Y, pipe, memScan);
                countInc();
                PC++;
                break;

            case 6: //Load value at (SP + X) into AC
                AC = readMemory(SP + X, pipe, memScan);
                countInc();
                PC++;
                break;

            case 7: //Write the value in AC to the following address
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                writeMemory(tempAddr, pipe, AC);
                countInc();
                PC++;
                break;

            case 8: //Put a random number 1-100 into AC
                Random rand = new Random();
                AC = rand.nextInt(100) + 1;
                countInc();
                PC++;
                break;

            case 9: //Print AC as an int or char if following val is 1 or 2 respectively
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                if(tempAddr == 1)
                {
                    System.out.print(AC);

                }
                else if (tempAddr == 2)
                {
                    System.out.print((char)AC);
                }
                else
                {
                    System.out.println("Error: " + tempAddr + "is not a port");
                    System.exit(0);
                }
                countInc();
                PC++;
                break;

            case 10: //Add X to AC
                AC = AC + X;
                countInc();
                PC++;
                break;

            case 11: //Add Y to AC
                AC = AC + Y;
                countInc();
                PC++;
                break;

            case 12: //Subtract X from AC
                AC = AC - X;
                countInc();
                PC++;
                break;

            case 13: //Subtract Y from AC
                AC = AC - Y;
                countInc();
                PC++;
                break;

            case 14: //Copy AC to X
                X = AC;
                countInc();
                PC++;
                break;

            case 15: //Copy X to AC
                AC = X;
                countInc();
                PC++;
                break;

            case 16: //Copy AC to Y
                Y = AC;
                countInc();
                PC++;
                break;

            case 17: //Copy Y to AC
                AC = Y;
                countInc();
                PC++;
                break;

            case 18: //Copy AC to SP
                SP = AC;
                countInc();
                PC++;
                break;

            case 19: //Copy SP to AC
                AC = SP;
                countInc();
                PC++;
                break;

            case 20: //Jump to following address
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                PC = tempAddr;
                countInc();
                break;

            case 21: //Jump to following address if AC is 0
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                if (AC == 0)
                {
                    PC = tempAddr - 1;
                }
                countInc();
                PC++;
                break;

            case 22: //Jump to following address if AC is not 0
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                if (AC != 0)
                {
                    PC = tempAddr - 1;
                }
                countInc();
                PC++;
                break;

            case 23: // jump to following address and push return address (PC + 1) onto the stack
                PC++;
                tempAddr = readMemory(PC, pipe, memScan);
                pushToStack(PC+1, pipe);
                //userStack = SP;
                PC = tempAddr;
                countInc();
                break;

            case 24: //Pop return address off of the stack and jump to it
                tempAddr = popFromStack(pipe, memScan);
                PC = tempAddr;
                countInc();
                break;

            case 25: //Increment X
                X++;
                countInc();
                PC++;
                break;

            case 26: //Decrement X
                X--;
                countInc();
                PC++;
                break;

            case 27: //Push AC onto the stack
                pushToStack(AC, pipe);
                PC++;
                countInc();
                break;

            case 28: //Pop AC off of the stack
                AC = popFromStack(pipe, memScan);
                PC++;
                countInc();
                break;

            case 29: // Interrupt/System call
                interrupt = true;
                mode = 1;
                tempAddr = SP;
                SP = 2000;
                pushToStack(tempAddr, pipe);

                tempAddr = PC + 1;
                PC = 1500;
                pushToStack(tempAddr, pipe);

                countInc();
                break;

            case 30: //Return from interrupt/system call
                PC = popFromStack(pipe, memScan);
                SP = popFromStack(pipe, memScan);
                mode = 0;
                instrCount++;
                interrupt = false;
                break;

            case 50: //End execution
                //countInc();
                System.exit(0);
                break;

            default: //Error
                System.out.println("Error: Instruction not recognized: " + IR);
                System.exit(0);
        }
    }

    public static void pushToStack(int value, PrintWriter pipe) {
        SP--;
        writeMemory(SP, pipe, value);
    }

    public static int popFromStack(PrintWriter pipe, Scanner memScan) {
        int value = readMemory(SP, pipe, memScan);
        SP++;
        return value; // return value;
        // note: stack items are lazily deleted by moving the pointer without clearing
        // could write a 0 before incrementing SP but I like to consider this a feature B>)
    }

    public static boolean shouldInterrupt(boolean interrupt, int count, int timer) {
        return !interrupt && (count % timer) == 0 && count != 0;
    }

    public static void countInc() {
        if(!interrupt) {
            instrCount++;
        }
    }

    public static int readMemory(int address, PrintWriter pipe, Scanner memScan) {

        if (address > 999 && mode == 0) {
            System.err.println("Attempted to read system memory in user mode");
            System.exit(0);
        }
        //System.out.println("Attempting to read from address: " + address);
        pipe.printf("1/" + address + "\n"); //setup a read from memory on 1
        pipe.flush();

        // check for memory response after request is sent
        if(memScan.hasNextInt()) {
            return memScan.nextInt();
        }
        return -1; // error
    }

    public static void writeMemory(int address, PrintWriter pipe, int value) {
        pipe.printf("2/" + address + "/" + value + "\n");
        pipe.flush();
    }
}