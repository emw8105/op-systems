#Overview
The project is a simulation of a hotel. It contains runs with a default of 25 guests, 2 front desk employees to check in guests, and 2 bellhops to deliver bags. It utilizes Java semaphores to track the actions and queues of various different processes. Certain actions are protected by mutexs to enforce mutual exclusion across data that would become race conditions (enqueueing/dequeueing, writing to values such as incrementing the ID, etc). Ultimately serves as a practice project to simulate parallelism and the challenges of it with mutual exclusion

#Getting Started
Run the project by entering into the terminal:
	javac Project2.java
	java Project2.java

#Notes
The numGuests integer can be modified to simulate different amounts of guests.
There is no max capacity in the hotel so it's an unfortunate day to be a hotel employee.