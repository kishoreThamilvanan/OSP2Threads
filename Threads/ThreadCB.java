package osp.Threads;

import java.util.Vector;
import java.util.*;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
 * Name 	: Kishore Thamilvanan
 * SBU ID 	: 111373510
 * 
 * I pledge my honor that all parts of this project were done by me individually, 
 *	 	without collaboration with anyone, and without consulting any external sources 
 *			that could help with similar projects.
 */


/**
 * This class is responsible for actions related to threads, including creating,
 * killing, dispatching, resuming, and suspending threads.
 * 
 * @OSPProject Threads
 */
public class ThreadCB extends IflThreadCB {

	static ArrayList<ThreadCB> ready_queue;

	/**
	 * The thread constructor. Must call
	 * 
	 * super();
	 * 
	 * as its first statement.
	 * 
	 * @OSPProject Threads
	 */
	public ThreadCB() {
		// out("I am in constructor");
		super();
	}

	/**
	 * This method will be called once at the beginning of the simulation. The
	 * student can set up static variables here.
	 * 
	 * @OSPProject Threads
	 */
	public static void init() {

		/* PriorityQueue<ThreadCB> */ ready_queue = new ArrayList<ThreadCB>();

	}

	/**
	 * Sets up a new thread and adds it to the given task. The method must set
	 * the ready status and attempt to add thread to task. If the latter fails
	 * because there are already too many threads in this task, so does this
	 * method, otherwise, the thread is appended to the ready queue and
	 * dispatch() is called.
	 * 
	 * The priority of the thread can be set using the getPriority/setPriority
	 * methods. However, OSP itself doesn't care what the actual value of the
	 * priority is. These methods are just provided in case priority scheduling
	 * is required.
	 * 
	 * @return thread or null
	 * @OSPProject Threads
	 */
	static public ThreadCB do_create(TaskCB task) {

		if (task == null || task.getThreadCount() >= MaxThreadsPerTask) {
			dispatch();
			return null;
		}

		ThreadCB new_thread = new ThreadCB();

		new_thread.setTask(task);
		new_thread.setPriority(task.getPriority());
		new_thread.setStatus(ThreadReady);

		if (task.addThread(new_thread) == FAILURE) {
			dispatch();
			return null;
		}

		else // placing it in the queue.
			ready_queue.add(new_thread);

		dispatch();
		// *******************

		MyOut.print(new_thread, "Do_create()");
		qstatus();

		// *******************
		return new_thread;

	}

	/**
	 * Kills the specified thread.
	 * 
	 * The status must be set to ThreadKill, the thread must be removed from the
	 * task's list of threads and its pending IORBs must be purged from all
	 * device queues.
	 * 
	 * If some thread was on the ready queue, it must removed, if the thread was
	 * running, the processor becomes idle, and dispatch() must be called to
	 * resume a waiting thread.
	 * 
	 * @OSPProject Threads
	 */
	public void do_kill() {
		
		if (this.getStatus() == ThreadReady) {
			ready_queue.remove(this);
		}

//		if(getPTBR().getTask().getCurrentThread() == this)
			
		if (this.getStatus() == ThreadRunning) {
			MMU.getPTBR().getTask().setCurrentThread(null);
			MMU.setPTBR(null);
		}

		this.setStatus(ThreadKill);
		
		int i = Device.getTableSize() - 1;
		while (i > -1) {

			Device.get(i).cancelPendingIO(this);
			i--;
		}
		
		ResourceCB.giveupResources(this);

		dispatch();

		if (this.getTask().getThreadCount() == 0)
			this.getTask().kill();

		// *******************

		this.out("Do_kill()");
		qstatus();

		// *******************

		return;

	}

	/**
	 * Suspends the thread that is currently on the processor on the specified
	 * event.
	 * 
	 * Note that the thread being suspended doesn't need to be running. It can
	 * also be waiting for completion of a pagefault and be suspended on the
	 * IORB that is bringing the page in.
	 * 
	 * Thread's status must be changed to ThreadWaiting or higher, the processor
	 * set to idle, the thread must be in the right waiting queue, and
	 * dispatch() must be called to give CPU control to some other thread.
	 * 
	 * @param event
	 *            - event on which to suspend this thread.
	 * 
	 * @OSPProject Threads
	 */
	public void do_suspend(Event event) {

		if (this.getStatus() == ThreadRunning){
			this.setStatus(ThreadWaiting);
			
			// we have to suspend the IORB of the thread. 
			MMU.setPTBR(null);
			this.getTask().setCurrentThread(null);
			event.addThread(this);
		}

		else if (this.getStatus() >= ThreadWaiting)
			this.setStatus(ThreadWaiting + 1);

		dispatch();

	}

	/**
	 * Resumes the thread.
	 * 
	 * Only a thread with the status ThreadWaiting or higher can be resumed. The
	 * status must be set to ThreadReady or decremented, respectively. A ready
	 * thread should be placed on the ready queue.
	 * 
	 * @OSPProject Threads
	 */
	public void do_resume() {

		this.status();

		if (this.getStatus() < ThreadWaiting) {

			dispatch();
			return;
		}

		if (this.getStatus() > ThreadWaiting)
			this.setStatus(this.getStatus()-1);
		
		else {

			this.setStatus(ThreadReady);
			ready_queue.add(this);
		}

		dispatch();

	}

	/**
	 * Selects a thread from the run queue and dispatches it.
	 * 
	 * If there is just one theread ready to run, reschedule the thread
	 * currently on the processor.
	 * 
	 * In addition to setting the correct thread status it must update the PTBR.
	 * 
	 * @return SUCCESS or FAILURE
	 * 
	 * @OSPProject Threads
	 */
	public static int do_dispatch() {
		System.out.print("In dispatch method");

		ThreadCB th =  new ThreadCB();
		
		// to get the currently running thread.
		ThreadCB current_thread = MMU.getPTBR().getTask().getCurrentThread();
		
		//suspend the currently running thread.
		if(current_thread != null){
			MMU.setPTBR(null);
			current_thread.getTask().setCurrentThread(null);
			
			//adding the current thread to the ready queue.
			current_thread.setStatus(ThreadReady);
			ready_queue.add(current_thread);
		}
		
		// now to dispatch a new thread from thr ready queue.
		if(ready_queue.size() > 0){
			th = ready_queue.remove(0);	
		}
				
		
		// Thread Waiting_time = thread.getCreationTime - thread.TimeonCPU.
		MMU.setPTBR(null);
		current_thread.getTask().setCurrentThread(null);
		return FAILURE;
	}

	/**
	 * Function to basically print messages easily
	 * 
	 * @param s
	 */
	public void out(String s) {
		MyOut.print(this, s);
	}

	/**
	 * Prints the essential details of the Thread.
	 * 
	 */
	public void status() {

		this.out("\n\nThread   :\t"); 
			this.out(""+this.getID());
		
		this.out("\nStatus   :\t"); 
			this.out(""+this.getStatus());
		
		this.out("\nPriority :\t"); 
			this.out(""+this.getPriority());
		
		this.out("\nTask     :\t"); 
			this.out(""+this.getTask());

	}

	/**
     *  Prints all the threads of the queues.
     */
    public static void qstatus(){
    	
    	int i=0;
    	while(i < ready_queue.size()){
    		(ready_queue.get(i)).getStatus();
    	}
    }

	/**
	 * Called by OSP after printing an error message. The student can insert
	 * code here to print various tables and data structures in their state just
	 * after the error happened. The body can be left empty, if this feature is
	 * not used.
	 * 
	 * @OSPProject Threads
	 */
	public static void atError() {

	}

	/**
	 * Called by OSP after printing a warning message. The student can insert
	 * code here to print various tables and data structures in their state just
	 * after the warning happened. The body can be left empty, if this feature
	 * is not used.
	 * 
	 * @OSPProject Threads
	 */
	public static void atWarning() {

	}

	/*
	 * Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
