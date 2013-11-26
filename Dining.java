//
// Simple Java implementation of the classic Dining Philosophers problem.
//
// No synchronization (yet).
//
// Can be run stand-alone or executed inside a web browser or applet viewer.
//
// Graphics are *very* naive.  Philosophers are big blobs.
// Forks are little blobs.
// 
// Written by Michael Scott, 1997; updated 2013 to use Swing.
//

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import java.util.*;
import java.lang.*;
import java.lang.Thread.*;

// This code has six main classes:
//  Dining
//      The public, "main" class.  Set up so the code can run either
//      stand-alone or as an applet in a web page or in appletviewer.
//  Philosopher
//      Active -- extends Thread
//  Fork
//      Passive
//  Table
//      Manages the philosophers and forks and their physical layout.
//  Coordinator
//      Provides mechanisms to suspend, resume, and reset the state of
//      worker threads (philosophers).
//  UI
//      Manages graphical layout and button presses.

public class Dining extends JApplet {
		static boolean runTests = false;
    private static final int CANVAS_SIZE = 360;
        // pixels in each direction;
        // needs to agree with size in dining.html

    private void start(final RootPaneContainer pane, final boolean isApplet) {
        final Coordinator c = new Coordinator();
        final Table t = new Table(c, CANVAS_SIZE,runTests);
        // arrange to call graphical setup from GUI thread
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    new UI(pane, c, t, isApplet);
                }
            });
        } catch (Exception e) {
            System.err.println("unable to create GUI");
        }
    }

    // called only when this is run as an applet:
    public void init() {
        start(this, true);
    }

    // called only when this is run as an application:
    public static void main(String[] args) {
        JFrame f = new JFrame("Dining");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				if(args.length > 0 && args[0].equals("-t")){
					runTests = true;
				}
        Dining me = new Dining();
        me.start(f, false);
        f.pack();            // calculate size of frame
        f.setVisible(true);
    }
}

class Fork {
    private Table t;
    private static final int XSIZE = 10;
    private static final int YSIZE = 10;
    private int orig_x;
    private int orig_y;
    private int x;
    private int y;
    public boolean requestR = false;
    public boolean requestL = false;
    public boolean clean = false;
    public boolean release2L = false;
    public boolean release2R = false;
    // Constructor.
    // cx and cy indicate coordinates of center.
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //
    public Fork(Table T, int cx, int cy) {
        t = T;
        orig_x = cx;
        orig_y = cy;
        x = cx;
        y = cy;
    }


    public void reset() {
        clear();
        x = orig_x;
        y = orig_y;
				clean = false;
				requestR = false;
				requestL = false;
				release2L = false;
				release2R = false;
        t.repaint();
    }

    // arguments are coordinates of acquiring philosopher's center
    //
    public void acquire(int px, int py) {
        clear();
        x = (orig_x + px)/2;
        y = (orig_y + py)/2;
        t.repaint();
    }

    public void release() {
        reset();
    }

    // render self
    //
    public void draw(Graphics g) {
        g.setColor(clean ? Color.black : Color.orange);
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    }
    // erase self
    //
    private void clear() {
        Graphics g = t.getGraphics();
        g.setColor(t.getBackground());
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    }
}
class BookKeeper extends TimerTask {
	public float optimalCount = 0;
	public float unoptimalCount = 0;
	//public int halfOptimalCount = 0;
	public float[] eatCount;
	public float sampleNum = 0;
	public boolean pinged = false;
	public Philosopher[] phils;

	public BookKeeper(Philosopher[] phils) {
		this.phils = phils;
		eatCount = new float[5];
		pinged = false;
		sampleNum = 0;
		optimalCount = 0;
		unoptimalCount = 0;
	}

	public void run() {
		if(sampleNum >= 1000){
			if(!pinged) System.out.println("ping");
			pinged = true;
			return;
		}
		sampleNum++;
		int eatCountNow = 0;
		boolean notOpt = false;
		
		for(int i = 0; i < phils.length; i++) {
			if (phils[i].isEating()) {
				eatCountNow++;
				eatCount[i]++;
				}
		}
		for(int i = 0; i < phils.length; i++) {
			//if one hungry, not eating, not next to eater and not 2 other eaters then not optimal
			Philosopher prev = phils[(i+1)%5];
			Philosopher next = phils[(i+4)%5];
			if(phils[i].isHungry() && !phils[i].isEating() && !(prev.isEating() || next.isEating()) && eatCountNow != 2) {
				unoptimalCount++;
				notOpt = true;
				break;
			}
		}
		if(!notOpt)
			optimalCount++;
	}
	public void printResults() {
		System.out.println("samples- "+sampleNum);
		System.out.println("agregate eating- "+eatCount[0]+" "+eatCount[1]+" "+eatCount[2]+" "+eatCount[3]+" "+eatCount[4]);
		System.out.println("agregate eating(normalized)- "+eatCount[0]/sampleNum+" "+eatCount[1]/sampleNum+" "+eatCount[2]/sampleNum+" "+eatCount[3]/sampleNum+" "+eatCount[4]/sampleNum);
		System.out.println("optimal- "+optimalCount);
		System.out.println("unoptimal- "+unoptimalCount);
		System.out.println("percentOpt = "+optimalCount /sampleNum * 100);
	}
}

class Philosopher extends Thread {
    private static final Color THINK_COLOR = Color.blue;
    private static final Color WAIT_COLOR = Color.red;
    private static final Color EAT_COLOR = Color.green;
    private static final Color FUMBLE_COLOR = Color.yellow;
    private static final double THINK_TIME = 4.0;
    private static final double FUMBLE_TIME = 2.0;
    // time between becoming hungry and grabbing first fork
    private static final double EAT_TIME = 3.0;
    private static final Font LABEL_FONT = new Font("Mono", Font.PLAIN, 24);
    private static final Color LABEL_COLOR = Color.white;

    private Coordinator c;
    private Table t;
    private static final int XSIZE = 50;
    private static final int YSIZE = 50;
    private int x;
    private int y;
    private Fork left_fork;
    private Fork right_fork;
    private Random prn;
    private Color color;
    int id;
    public boolean hasForkLeft = false;
    public boolean hasForkRight = true;

    // Constructor.
    // cx and cy indicate coordinates of center
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //
    public Philosopher(Table T, int cx, int cy,
                       Fork lf, Fork rf, Coordinator C, int id) {
        t = T;
        x = cx;
        y = cy;
        left_fork = lf;
        right_fork = rf;
        c = C;
        prn = new Random();
        color = THINK_COLOR;
        this.id = id;
				reset();
        //System.out.println("phil "+id+" hand status- "+hasForkLeft+" "+hasForkRight);
    }

		public boolean isHungry()
		{return color == WAIT_COLOR;}
		public boolean isEating() 
		{return color == EAT_COLOR;}

		public void reset() {
			hasForkLeft = false;
			hasForkRight = true;
			if(id == 0 || id ==  3)
				hasForkLeft = true;
			if(id == 4 ||id == 2)
				hasForkRight = false;
		}

    // start method of Thread calls run; you don't
    //
    public void run() {
        boolean first_run = true;
        for (;;) {
            try {
                if (first_run && c.gate()) {
                    first_run = false;
                    if(hasForkLeft) {
                        //System.out.println("phil "+id+" getting left");
                        acquire(false);
                    }
                    if(hasForkRight){
                        //System.out.println("phil "+id+" getting right");
                        acquire(true);
                    }
                }
                if (c.gate()) delay(EAT_TIME/2.0);
                think();
                if (c.gate()) delay(THINK_TIME/2.0);
                hunger();
                if (c.gate()) delay(FUMBLE_TIME/2.0);
                eat();
            } catch(ResetException e) {
                color = THINK_COLOR;
                first_run = true;
                t.repaint();
								reset();
								left_fork.reset();
								right_fork.reset();
            }
        }
    }

    // render self
    //
    public void draw(Graphics g) {
        g.setColor(color);
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
        g.setFont(LABEL_FONT);
        g.setColor(LABEL_COLOR);
        g.drawString(""+this.id, x-XSIZE/7, y+YSIZE/5);
    }

    // sleep for secs +- FUDGE (%) seconds
    //
    private static final double FUDGE = 0.2;
    private void delay(double secs) throws ResetException {
        double ms = 1000 * secs;
        int window = (int) (2.0 * ms * FUDGE);
        int add_in = prn.nextInt() % window;
        int original_duration = (int) ((1.0-FUDGE) * ms + add_in);
        int duration = original_duration;
        for (;;) {
            try {
                Thread.sleep(duration);
                return;
            } catch(InterruptedException e) {
                if (c.isReset()) {
                    throw new ResetException();
                } else {        // suspended
                    c.gate();   // wait until resumed
                    duration = original_duration / 2;
                    // don't wake up instantly; sleep for about half
                    // as long as originally instructed
                }
            }
        }
    }

    public void acquire(boolean isRight) {
        if(isRight) {
            hasForkRight = true;
            right_fork.requestL = false;
            right_fork.release2L = false;
            right_fork.acquire(x,y);
        }
        else {
            hasForkLeft = true;
            left_fork.requestR = false;
            left_fork.release2R = false;
            left_fork.acquire(x,y);
        }
    }

    public void release(boolean isRight) {
        if(isRight) {
            hasForkRight = true;
            right_fork.requestL = false;
            right_fork.release2L = false;
            right_fork.acquire(x,y);
        }
        else {
            hasForkLeft = true;
            left_fork.requestR = false;
            left_fork.release2R = false;
            left_fork.acquire(x,y);
        }
    }

    private void think() throws ResetException {
        color = THINK_COLOR;
        t.repaint();
        delay(THINK_TIME);
    }

    private void hunger() throws ResetException {
        //System.out.println("philosopher "+id+"is hungry");
        left_fork.clean = true;
        color = FUMBLE_COLOR;//WAIT_COLOR;
        t.repaint();
        delay(FUMBLE_TIME);
				color = WAIT_COLOR;
        if (!hasForkLeft) {
            left_fork.requestR = true;
        }
        yield();    // you aren't allowed to remove this
        if (!hasForkRight) {
            right_fork.requestL = true;
        }
        c.gate();
        while(!hasForkLeft || !hasForkRight) {
                // fork has been release to us- grab it
            if (!hasForkRight && right_fork.release2L){// && right_fork.clean) {
                acquire(true);
            }
                // fork has been release to us- grab it
            if (!hasForkLeft && left_fork.release2R){ //&& left_fork.clean){
                acquire(false);
            }
                //someone else wants the fork and it's dirty: clean and give
            if (hasForkRight && !right_fork.clean && right_fork.requestR){
                right_fork.clean = true;
                right_fork.release();
                hasForkRight = false;
                right_fork.release2R = true;
                right_fork.requestL = true;
            }
                //someone else wants the fork and it's dirty: clean and give
            if(hasForkLeft && !left_fork.clean && left_fork.requestL){
                left_fork.clean = true;
                left_fork.release();
                hasForkLeft = false;
                left_fork.release2L = true;
                left_fork.requestR = true;
            }
						c.gate();
        }
            //System.out.println("startin my dinner");
    }

    private void eat() throws ResetException {
        color = EAT_COLOR;
        t.repaint();
        delay(EAT_TIME);
        left_fork.clean = false;
        right_fork.clean = false;

        if (left_fork.requestL) {
            left_fork.clean = true;
            left_fork.release2L = true;
            hasForkLeft = false;
        }
        else {
            left_fork.release2L = true;
            hasForkLeft = false;
        }
				yield();    // you aren't allowed to remove this
        if (right_fork.requestR) {
            right_fork.clean = true;
            right_fork.release2R = true;
            hasForkRight = false;
        }
        else {
            right_fork.release2R = true;
            hasForkRight = false;
        }
    }
}

// Graphics panel in which philosophers and forks appear.
//
class Table extends JPanel {
    private static final int NUM_PHILS = 5;

    // following fields are set by construcctor:
    private final Coordinator c;
    private Fork[] forks;
    private Philosopher[] philosophers;
		private BookKeeper booky;
		private java.util.Timer timer;
		private boolean runTests = false;

    public void pause() {
        c.pause();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
				if(runTests) {
					timer.cancel();
					timer = new java.util.Timer();
					booky = new BookKeeper(philosophers);
				}
    }

    // Called by the UI when it wants to start over.
    //
    public void reset() {
        c.reset();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].reset();
        }
				if(runTests) {
					timer.cancel();
					booky.printResults();
					timer = new java.util.Timer();
					booky = new BookKeeper(philosophers);
				}
    }

    // The following method is called automatically by the graphics
    // system when it thinks the Table canvas needs to be re-displayed.
    // This can happen because code elsewhere in this program called
    // repaint(), or because of hiding/revealing or open/close
    // operations in the surrounding window system.
    //
    public void paintComponent(Graphics g) {
        // turn on anti-aliasing
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        super.paintComponent(g);

        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].draw(g);
            philosophers[i].draw(g);
        }
        g.setColor(Color.black);
        g.drawRect(0, 0, getWidth()-1, getHeight()-1);
    }

    // Constructor
    //
    // Note that angles are measured in radians, not degrees.
    // The origin is the upper left corner of the frame.
    //
    public Table(Coordinator C, int CANVAS_SIZE,boolean runt) {    // constructor
        c = C;
        forks = new Fork[NUM_PHILS];
        philosophers = new Philosopher[NUM_PHILS];
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
				runTests = runt;
				System.out.println("runtests in table is- "+runt);
        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI/2 + 2*Math.PI/NUM_PHILS*(i-0.5);
            forks[i] = new Fork(this,
                (int) (CANVAS_SIZE/2.0 + CANVAS_SIZE/6.0 * Math.cos(angle)),
                (int) (CANVAS_SIZE/2.0 - CANVAS_SIZE/6.0 * Math.sin(angle)));
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI/2 + 2*Math.PI/NUM_PHILS*i;
            philosophers[i] = new Philosopher(this,
                (int) (CANVAS_SIZE/2.0 + CANVAS_SIZE/3.0 * Math.cos(angle)),
                (int) (CANVAS_SIZE/2.0 - CANVAS_SIZE/3.0 * Math.sin(angle)),
                forks[i],
                forks[(i+1) % NUM_PHILS],
                c,
                i);
						philosophers[i].start();
				}
				timer = new java.util.Timer();
				booky = new BookKeeper(philosophers);
			}

	public void startTests() {
				if(runTests) {
					timer.scheduleAtFixedRate(booky,10,10);
				}
    }
}

class ResetException extends Exception { };

// The Coordinator serves to slow down execution, so that behavior is
// visible on the screen, and to notify all running threads when the user
// wants them to reset.
//
class Coordinator {
    public enum State { PAUSED, RUNNING, RESET }
    private State state = State.PAUSED;
		private BookKeeper booky;
		private java.util.Timer timer;

    public synchronized boolean isPaused() {
        return (state == State.PAUSED);
    }

    public synchronized void pause() {
        state = State.PAUSED;
    }

    public synchronized boolean isReset() {
        return (state == State.RESET);
    }

    public synchronized void reset() {
        state = State.RESET;
    }

    public synchronized void resume() {
        state = State.RUNNING;
        notifyAll();        // wake up all waiting threads
    }

    // Return true if we were forced to wait because the coordinator was
    // paused or reset.
    //
    public synchronized boolean gate() throws ResetException {
        if (state == State.PAUSED || state == State.RESET) {
            try {
                wait();
            } catch(InterruptedException e) {
                if (isReset()) {
                    throw new ResetException();
                }
            }
            return true;        // waited
        }
        return false;           // didn't wait
    }
}

// Class UI is the user interface.  It displays a Table canvas above
// a row of buttons.  Actions (event handlers) are defined for each of
// the buttons.  Depending on the state of the UI, either the "run" or
// the "pause" button is the default (highlighted in most window
// systems); it will often self-push if you hit carriage return.
//
class UI extends JPanel {
    private final Coordinator c;
    private final Table t;

    private final JRootPane root;
    private static final int externalBorder = 6;

    private static final int stopped = 0;
    private static final int running = 1;
    private static final int paused = 2;

    private int state = stopped;

    // Constructor
    //
    public UI(RootPaneContainer pane, Coordinator C, Table T,
              boolean isApplet) {
        final UI u = this;
        c = C;
        t = T;

        final JPanel b = new JPanel();   // button panel

        final JButton runButton = new JButton("Run");
        final JButton pauseButton = new JButton("Pause");
        final JButton resetButton = new JButton("Reset");
        final JButton quitButton = new JButton("Quit");

        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                c.resume();
								t.startTests();
                root.setDefaultButton(pauseButton);
            }
        });
        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.pause();
                root.setDefaultButton(runButton);
            }
        });
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.reset();
                root.setDefaultButton(runButton);
            }
        });
        // Applets can't quit, so don't show the quit button in applet mode
        if (!isApplet) {
            quitButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.exit(0);
                }
            });
        }

        // put the buttons into the button panel:
        b.setLayout(new FlowLayout());
        b.add(runButton);
        b.add(pauseButton);
        b.add(resetButton);
        if (!isApplet) {
            b.add(quitButton);
        }

        // put the Table canvas and the button panel into the UI:
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
            externalBorder, externalBorder, externalBorder, externalBorder));
        add(t);
        add(b);

        // put the UI into the Frame or Applet:
        pane.getContentPane().add(this);
        root = getRootPane();
        root.setDefaultButton(runButton);
    }
}
