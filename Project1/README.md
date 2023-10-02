CS4348 - Project 1: Exploring Multiple Processes and IPC

Files Included:

	CPU.java - The main driver process, simulates a CPU and
		includes the registers, instructions, as well as setup 		code to create processes and initialize everything
	
	Memory.java - Separate process created from the CPU, sets up an array of integers of size 2000 to simulate main memory, recieves instructions to read/write at specific indexes from the CPU and sends them back using print statements

How to Compile and Run:

1. javac CPU.java Memory.java

2. java (name of file as a string).txt (interrupt timer amount as an integer)

Example: java CPU.java sample1.txt 30
