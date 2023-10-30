import java.util.Queue;
import java.util.Random;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

class Project2 {
    private static int nextRoom = 0;
    private static boolean finished = false;

    private static Semaphore mutex1 = new Semaphore(1);
    private static Semaphore mutex2 = new Semaphore(1);
    private static Semaphore mutex3 = new Semaphore(1);
    private static Semaphore mutex4 = new Semaphore(1);
    private static Semaphore front_desk_available = new Semaphore(2);
    private static Semaphore guest_ready = new Semaphore(0);
    private static Semaphore[] checked_In = new Semaphore[25];
    private static Semaphore leave_front_desk = new Semaphore(0);
    private static Semaphore bellhop_available = new Semaphore(2);
    private static Semaphore request_ready = new Semaphore(0);
    private static Semaphore ready_to_get_bags = new Semaphore(0);
    private static Semaphore receive_bags = new Semaphore(0);

    private static Queue<Integer> queue1 = new LinkedList<>();
    private static Queue<Integer> queue2 = new LinkedList<>();
    private static Queue<Integer> queue3 = new LinkedList<>();
    private static Queue<Integer> queue4 = new LinkedList<>();
    private static Queue<Integer> queue5 = new LinkedList<>();

    static {
        for (int i = 0; i < 25; i++) {
            checked_In[i] = new Semaphore(0);
        }
    }

    public static void main(String[] args) {
        int numGuests = 25;
        Thread[] guestThreads = new Thread[numGuests];
        Thread[] frontDeskThreads = new Thread[2];
        Thread[] bellhopThreads = new Thread[2];

        System.out.println("Simulation starts");

        // create 2 front desk employees
        for (int i = 0; i < 2; i++) {
            frontDeskThreads[i] = new Thread(new FrontDeskEmployee(i));
            System.out.println("Front desk employee " + i + " created");
            frontDeskThreads[i].start();
        }

        // create 2 bellhops
        for (int i = 0; i < 2; i++) {
            bellhopThreads[i] = new Thread(new Bellhop(i));
            System.out.println("Bellhop " + i + " created");
            bellhopThreads[i].start();
        }

        // create 25 guests
        for (int i = 0; i < numGuests; i++) {
            guestThreads[i] = new Thread(new Guest(i));
            System.out.println("Guest " + i + " created");
            guestThreads[i].start();
        }

        // Wait for all guest threads to finish
        for (int i = 0; i < numGuests; i++) {
            try {
                guestThreads[i].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }

        //Interrupt front desk and bellhop threads to exit the loops
        for (int i = 0; i < 2; i++) {
            frontDeskThreads[i].interrupt();
            bellhopThreads[i].interrupt();
        }
        System.out.println("Simulation ends");
        finished = true;
    }

    public static class Guest implements Runnable {
        int guestNum;
        int bags;

        public Guest(int guestNum) {
            this.guestNum = guestNum;
            this.bags = (int) (Math.random() * 6);
        }

        @Override
        public void run() {
            try {
                System.out.println("Guest " + guestNum + " enters the hotel with " + bags + " bag" + (bags != 1 ? "s" : "")); // enterHotel();

                front_desk_available.acquire();
                mutex1.acquire();
                queue1.offer(guestNum); // enqueue(guestNum), make front desk be fair to check in guests one at a time
                guest_ready.release();
                mutex1.release();

                checked_In[guestNum].acquire(); // wait for this specific guest to be dismissed
                mutex2.acquire();
                int assignedRoom = queue2.poll();
                int frontDeskID = queue3.poll();
                System.out.println("Guest " + guestNum + " receives room key for room " + assignedRoom + " from front desk employee " + frontDeskID); // leave_desk()
                mutex2.release();

                if (bags > 2) {
                    bellhop_available.acquire();
                    mutex3.acquire();
                    queue4.offer(guestNum);
                    request_ready.release();
                    System.out.println("Guest " + guestNum + " requests help with bags");
                    leave_front_desk.release();
                    mutex3.release();

                    System.out.println("Guest " + guestNum + " enters room " + assignedRoom); // go_to_room()
                    ready_to_get_bags.release();
                    receive_bags.acquire();
                    mutex4.acquire();
                    int bellhopID = queue5.poll();
                    System.out.println("Guest " + guestNum + " receives bags from bellhop " + bellhopID + " and gives tip");
                    mutex4.release();
                    System.out.println("Guest " + guestNum + " retires for the evening");// retire()
                } else {
                    leave_front_desk.release();
                    System.out.println("Guest " + guestNum + " enters room " + assignedRoom); // go_to_room()
                    System.out.println("Guest " + guestNum + " retires for the evening");// retire()
                }
                System.out.println("Guest " + guestNum + " joined");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class FrontDeskEmployee implements Runnable {
        int frontDeskID;

        public FrontDeskEmployee(int frontDeskID) {
            this.frontDeskID = frontDeskID;
        }

        @Override
        public void run() {
            while (finished == false) {
                try {
                    guest_ready.acquire();
                    mutex1.acquire();
                    int guestNum = queue1.poll();
                    nextRoom++;
                    int assignedRoom = nextRoom;
                    queue2.offer(nextRoom);
                    mutex1.release();

                    mutex2.acquire();
                    queue3.offer(frontDeskID);
                    mutex2.release();

                    System.out.println("Front desk employee " + frontDeskID + " registers guest " + guestNum + " and assigns room " + assignedRoom);
                    checked_In[guestNum].release();
                    
                    leave_front_desk.acquire(); // wait for current guest to leave before accepting the next
                    front_desk_available.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static class Bellhop implements Runnable {
        int bellhopID;

        public Bellhop(int bellhopID) {
            this.bellhopID = bellhopID;
        }

        @Override
        public void run() {
            while (finished == false) {
                try {
                    request_ready.acquire();
                    mutex3.acquire();
                    int guestNum = queue4.poll();
                    mutex3.release();

                    System.out.println("Bellhop " + bellhopID + " receives bags from guest " + guestNum); // receive_bag()

                    mutex4.acquire();
                    queue5.offer(bellhopID);
                    mutex4.release();

                    ready_to_get_bags.acquire();
                    System.out.println("Bellhop " + bellhopID + " delivers bags to guest " + guestNum); // bags_delivered()

                    receive_bags.release();
                    bellhop_available.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}