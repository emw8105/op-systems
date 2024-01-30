#Overview
The project is a simulation of various file allocation methods, namely contiguous, chained, and indexed allocation. It uses an interface to manage the file allocation methods so that the calling function will always successfully execute the desired method regardless of which file allocation method is chosen. The user interacts with the User Interface, which calls the File System, which abstracts each file allocation method class by calling the methods from the interface. While each allocation method has a different implementation, they all aim to perform the same tasks regardless of which one the user selects, with the goal of making it indistinguishable in terms of effectiveness. Ultimately, this serves as a practice simulation for learning about file allocation methods and the file system in general

#Getting Started
Run the project by changing to the /src directory
	You can run "cd src" once in the termal to ensure that you are in the right directory to access the files
Then, run the project by entering into the terminal:
	javac Project3.java
	java Project3 allocationMethod
	(where allocationMethod is either contiguous, chained, or indexed)

#Notes
This program could be simplified and refined further if I wasn't on a time crunch to implement the features, I might fix it up later