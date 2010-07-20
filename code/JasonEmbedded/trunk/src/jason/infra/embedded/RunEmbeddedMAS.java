//----------------------------------------------------------------------------
// Copyright (C) 2003  Rafael H. Bordini and Jomi F. Hubner
// 
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
// 
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// 
// To contact the authors:
// http://www.dur.ac.uk/r.bordini
// http://www.inf.furb.br/~jomi
//
//----------------------------------------------------------------------------

package jason.infra.embedded;

import jason.JasonException;
import jason.asSemantics.Agent;
import jason.asSyntax.directives.DirectiveProcessor;
import jason.asSyntax.directives.Include;
import jason.control.ExecutionControlGUI;
import jason.jeditplugin.Config;
import jason.mas2j.AgentParameters;
import jason.mas2j.ClassParameters;
import jason.mas2j.MAS2JProject;
import jason.mas2j.parser.ParseException;
import jason.runtime.Settings;
import jason.runtime.console.MASConsole;
import jason.runtime.console.MASConsoleLogFormatter;
import jason.runtime.console.MASConsoleLogHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/**
 * Runs MASProject using centralised infrastructure.
 */
public class RunEmbeddedMAS {

    public final static String       logPropFile     = "logging.properties";
    public final static String       stopMASFileName = ".stop___MAS";
    public final static String       defaultProjectFileName = "default.mas2j";

    private   static Logger            logger        = Logger.getLogger(RunEmbeddedMAS.class.getName());
    protected static RunEmbeddedMAS    runner        = null;
    private   static String            urlPrefix     = "";
    private   static boolean           readFromJAR   = false;
    private   static MAS2JProject      project;
    
    private EmbeddedEnvironment        env         = null;
    private EmbeddedExecutionControl   control     = null;
    private boolean                       debug       = false;
    private Map<String,EmbeddedAgArch> ags    = new ConcurrentHashMap<String,EmbeddedAgArch>();

    public JButton                   btDebug;
    
    public RunEmbeddedMAS() {
        runner = this;
    }
    
    public static void main(String[] args) {
        runner = new RunEmbeddedMAS();
        runner.init(args);
    }
    
    public void init(String projectFileName, boolean debug) {
    	if(projectFileName == null) {
    		if (RunEmbeddedMAS.class.getResource("/"+defaultProjectFileName) != null) {
        		projectFileName = defaultProjectFileName;
        		readFromJAR = true;
        		Config.get(false); // to void to call fix/store the configuration in this case everything is read from a jar/jnlp file
        	} else {
        		System.err.println("You should inform the MAS project file.");
        		System.exit(1);
        	}
    	}
    	
    	setupLogger();
    	
    	if(debug) {
    		Logger.getLogger("").setLevel(Level.FINE);
    	}
    	
    	// discover the handler
        for (Handler h : Logger.getLogger("").getHandlers()) {
            // if there is a MASConsoleLogHandler, show it
            if (h.getClass().toString().equals(MASConsoleLogHandler.class.toString())) {
                MASConsole.get().setAsDefaultOut();
            }
        }

        int errorCode = 0;

        try {
        	InputStream inProject;
        	if (readFromJAR) {
        		inProject = RunEmbeddedMAS.class.getResource("/"+defaultProjectFileName).openStream();
        		urlPrefix = Include.CRPrefix + "/";
        	} else {
	        	URL file;
	        	// test if the argument is an URL
	        	try {
	        		file = new URL(projectFileName);
	        		if (projectFileName.startsWith("jar")) {
	        			urlPrefix = projectFileName.substring(0,projectFileName.indexOf("!")+1) + "/";
	        		}
	        	} catch (Exception e) {
	        		file = new URL("file:"+projectFileName);
	        	}
	        	inProject = file.openStream();
        	}
            jason.mas2j.parser.mas2j parser = new jason.mas2j.parser.mas2j(inProject); 
            project = parser.mas();
            project.setupDefault();

            project.registerDirectives();
            
            runner.createAg(project, debug);
            runner.startAgs();
            runner.startSyncMode();

            if (MASConsole.hasConsole()) {
                MASConsole.get().setTitle("MAS Console - " + project.getSocName());

                createGUI();
            }

            runner.waitEnd();
            errorCode = 0;

        } catch (FileNotFoundException e1) {
            logger.log(Level.SEVERE, "File " + projectFileName + " not found!");
            errorCode = 2;
        } catch (ParseException e) {
            logger.log(Level.SEVERE, "Error parsing file " + projectFileName + "!", e);
            errorCode = 3;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error!?: ", e);
            errorCode = 4;
        }
        System.out.flush();
        System.err.flush();

        if (!MASConsole.hasConsole() && errorCode != 0) {
            System.exit(errorCode);
        }
    }
    
    public void init(String[] args) {
    	String projectFileName = null;
        if (args.length > 0) {
        	projectFileName = args[0];        	
        }

        if (args.length == 2) {
            if (args[1].equals("-debug")) {
                debug = true;
            }
        }
        
        this.init(projectFileName, debug);
    }

    public static boolean isDebug() {
        return runner.debug;
    }

    public static void setupLogger() {
    	if (readFromJAR) {
    		Handler[] hs = Logger.getLogger("").getHandlers(); 
    		for (int i = 0; i < hs.length; i++) { 
    			Logger.getLogger("").removeHandler(hs[i]); 
    		}
    		Handler h = new MASConsoleLogHandler();
    		h.setFormatter(new MASConsoleLogFormatter()); 
    		Logger.getLogger("").addHandler(h);
    		Logger.getLogger("").setLevel(Level.INFO);
    	} else {
	        // see for a local log configuration
	        if (new File(logPropFile).exists()) {
	            try {
	                LogManager.getLogManager().readConfiguration(new FileInputStream(logPropFile));
	            } catch (Exception e) {
	                System.err.println("Error setting up logger:" + e);
	            }
	        } else {
	            try {
	                LogManager.getLogManager().readConfiguration(RunEmbeddedMAS.class.getResource("/templates/" + logPropFile).openStream());
	            } catch (Exception e) {
	                System.err.println("Error setting up logger:" + e);
	                e.printStackTrace();
	            }
	        }
    	}
    }
    
    public static void setupDefaultConsoleLogger() {
        Handler[] hs = Logger.getLogger("").getHandlers(); 
        for (int i = 0; i < hs.length; i++) { 
            Logger.getLogger("").removeHandler(hs[i]); 
        }
        Handler h = new ConsoleHandler();
        h.setFormatter(new MASConsoleLogFormatter()); 
        Logger.getLogger("").addHandler(h);
        Logger.getLogger("").setLevel(Level.INFO);        
    }

    protected void createGUI() {
        
    }
    
    public static RunEmbeddedMAS getRunner() {
        return runner;
    }

    public EmbeddedExecutionControl getControllerInfraTier() {
        return control;
    }

    public EmbeddedEnvironment getEnvironmentInfraTier() {
        return env;
    }
    
    public MAS2JProject getProject() {
        return project;
    }

    protected void createAg(MAS2JProject project, boolean debug) throws JasonException {
        // create environment
        logger.fine("Creating environment " + project.getEnvClass());
        env = new EmbeddedEnvironment(project.getEnvClass(), this);

        boolean isPool = project.getInfrastructure().hasParameter("pool");
        if (isPool) logger.info("Creating agents....");
        int nbAg = 0;
        Agent pag = null;
        
        project.fixAgentsSrc(urlPrefix);
        
        // set the aslSrcPath in the include
        ((Include)DirectiveProcessor.getDirective("include")).setSourcePath(project.getSourcePaths());
        
        // create the agents
        for (AgentParameters ap : project.getAgents()) {
            try {
                ap.setupDefault();
                
                String agName = ap.name;

                for (int cAg = 0; cAg < ap.qty; cAg++) {
                    nbAg++;
                    
                    String numberedAg = agName;
                    if (ap.qty > 1) {
                        numberedAg += (cAg + 1);
                    }
                    logger.fine("Creating agent " + numberedAg + " (" + (cAg + 1) + "/" + ap.qty + ")");
                    EmbeddedAgArch agArch;
                    if (isPool) {
                        agArch = new CentralisedAgArchForPool();
                    } else {
                        agArch = new EmbeddedAgArch();
                    }
                    agArch.setAgName(numberedAg);
                    agArch.setEnvInfraTier(env);
                    if (isPool && cAg > 0) {
                        // creation by cloning previous agent
                        agArch.initAg(ap.archClass.getClassName(), pag, this);
                    } else {
                        // normal creation
                        agArch.initAg(ap.archClass.getClassName(), ap.agClass.getClassName(), ap.getBBClass(), ap.asSource.toString(), ap.getAsSetts(debug, project.getControlClass() != null), this);
                    }
                    addAg(agArch);
                    
                    pag = agArch.getUserAgArch().getTS().getAg();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error creating agent " + ap.name, e);
            }
        }
        
        if (isPool) logger.info("Created "+nbAg+" agents.");

        // create controller
        ClassParameters controlClass = project.getControlClass();
        if (debug && controlClass == null) {
            controlClass = new ClassParameters(ExecutionControlGUI.class.getName());
        }
        if (controlClass != null) {
            logger.fine("Creating controller " + controlClass);
            control = new EmbeddedExecutionControl(controlClass, this);
        }
    }

    public void addAg(EmbeddedAgArch ag) {
    	ags.put(ag.getAgName(), ag);
    }
    public EmbeddedAgArch delAg(String agName) {
    	return ags.remove(agName);
    }
    
    public EmbeddedAgArch getAg(String agName) {
    	return ags.get(agName);
    }
    
    protected Map<String,EmbeddedAgArch> getAgs() {
    	return ags;
    }
    
    protected void startAgs() {
        // run the agents
        if (project.getInfrastructure().hasParameter("pool")) {
            createThreadPool();
        } else {
            createAgsThreads();
        }
    }
    
    private void createAgsThreads() {
        for (EmbeddedAgArch ag : ags.values()) {
            ag.setControlInfraTier(control);
            
            // create the agent thread
            Thread agThread = new Thread(ag);
            ag.setThread(agThread);
            agThread.start();
        }        
    }
    
    private BlockingQueue<Runnable> myAgTasks;
    private BlockingQueue<Runnable> mySleepAgs;
    
    private void createThreadPool() {
        myAgTasks = new LinkedBlockingQueue<Runnable>();
        mySleepAgs = new LinkedBlockingQueue<Runnable>();
        
        new Thread("feed-pool") {
            public void run() {
                // initially, add all agents in the tasks
                for (EmbeddedAgArch ag : ags.values()) {
                    myAgTasks.offer(ag);
                }
                
                // get the max number of threads in the pool
                int maxthreads = 10;
                try {
                    if (project.getInfrastructure().hasParameters()) {
                        maxthreads = Integer.parseInt(project.getInfrastructure().getParameter(1));
                        logger.info("Creating a thread pool with "+maxthreads+" thread(s).");
                    }
                } catch (Exception e) {
                    logger.warning("Error getting the number of thread for the pool.");
                }

                // define pool size
                int poolSize = ags.size();
                if (poolSize > maxthreads) {
                    poolSize = maxthreads;
                }
                ExecutorService executor = Executors.newFixedThreadPool(poolSize);
                while (runner != null) {
                    try {
                        executor.execute(myAgTasks.take());
                    } catch (InterruptedException e) { }
                }
                executor.shutdownNow();
            }
        }.start();
        
        // create a thread that wakeup the sleeping agents
        new Thread("wake-sleep-ag") {
            public void run() {
                while (runner != null) {
                    try {
                        Runnable ag = mySleepAgs.poll();
                        while (ag != null) {
                            myAgTasks.offer(ag);
                            ag = mySleepAgs.poll();
                        }
                        sleep(2000);
                    } catch (InterruptedException e) { }
                }
            }            
        }.start();
    }
    
    private final class CentralisedAgArchForPool extends EmbeddedAgArch {
        boolean inSleep;
        
        @Override
        public void sleep() { 
            mySleepAgs.offer(this);
            inSleep = true;
        }

        @Override
        public void wake() {
            if (mySleepAgs.remove(this)) {
                myAgTasks.offer(this);
            }
        }
        
        @Override
        public void run() {
            if (isRunning()) { 
                inSleep = false;
                userAgArch.getTS().reasoningCycle();
                if (!inSleep) myAgTasks.offer(this);
            }
        }
    }
    
    protected void stopAgs() {
        // run the agents
        for (EmbeddedAgArch ag : ags.values()) {
            ag.stopAg();
        }
    }

    /** change the current running MAS to debug mode */
    void changeToDebugMode() {
        try {
            if (control == null) {
                control = new EmbeddedExecutionControl(new ClassParameters(ExecutionControlGUI.class.getName()), this);
                for (EmbeddedAgArch ag : ags.values()) {
                    ag.setControlInfraTier(control);
                    Settings stts = ag.getUserAgArch().getTS().getSettings();
                    stts.setVerbose(2);
                    stts.setSync(true);
                    ag.getLogger().setLevel(Level.FINE);
                    ag.getUserAgArch().getTS().getLogger().setLevel(Level.FINE);
                    ag.getUserAgArch().getTS().getAg().getLogger().setLevel(Level.FINE);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error entering in debug mode", e);
        }
    }

    protected void startSyncMode() {
        if (control != null) {
            // start the execution, if it is controlled
            try {
                Thread.sleep(500); // gives a time to agents enter in wait
                control.informAllAgsToPerformCycle(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void waitEnd() {
        try {
            // wait a file called .stop___MAS to be created!
            File stop = new File(stopMASFileName);
            if (stop.exists()) {
                stop.delete();
            }
            while (!stop.exists()) {
                Thread.sleep(1500);
            }
            if (stop.exists()) {
                stop.delete();
            }
            finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void finish() {
        
        try {
            // creates a thread that guarantees system.exit(0) in 5 seconds
            // (the stop of agents can  block)
            new Thread() {
                public void run() {
                    try {
                        sleep(5000);
                    } catch (InterruptedException e) {}
                    System.exit(0);
                }
            }.start();
            
            if (MASConsole.hasConsole()) { // should close first! (case where console is in pause)
                MASConsole.get().close();
            }

            if (control != null) {
                control.stop();
                control = null;
            }
            if (env != null) {
                env.stop();
                env = null;
            }
            
            stopAgs();

            runner = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(0);
    }
    
    /** show the sources of the project */
    private static void showProjectSources(MAS2JProject project) {
        JFrame frame = new JFrame("Project "+project.getSocName()+" sources");
        JTabbedPane pane = new JTabbedPane();
        frame.getContentPane().add(pane);
        project.fixAgentsSrc(urlPrefix);

        for (AgentParameters ap : project.getAgents()) {
            try {
            	String tmpAsSrc = ap.asSource.toString();
                
                // read sources
                InputStream in = null;
                if (tmpAsSrc.startsWith(Include.CRPrefix)) {
                    in = RunEmbeddedMAS.class.getResource(tmpAsSrc.substring(Include.CRPrefix.length())).openStream();
                } else {
                    try {
                        in = new URL(tmpAsSrc).openStream(); 
                    } catch (MalformedURLException e) {
                        in = new FileInputStream(tmpAsSrc);
                    }
                }
                StringBuilder s = new StringBuilder();
                int c = in.read();
                while (c > 0) {
                    s.append((char)c);
                    c = in.read();
                }
                
                // show sources
                JTextArea ta = new JTextArea(40,50);
                ta.setEditable(false);
                ta.setText(s.toString());
                ta.setCaretPosition(0);
                JScrollPane sp = new JScrollPane(ta);
                pane.add(ap.name, sp);
            } catch (Exception e) {
                logger.info("Error:"+e);
            }
        }
        frame.pack();
        frame.setVisible(true);
    }
}
