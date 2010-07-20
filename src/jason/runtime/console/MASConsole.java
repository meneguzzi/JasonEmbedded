package jason.runtime.console;

public abstract class MASConsole {
	public static MASConsole masConsole = null;
	
	public static MASConsole get() {
		if(masConsole == null) {
			String consoleClass = System.getProperty("mas.console");
			//Our default behaviour is this one.
			if(consoleClass != null) {
				try {
					masConsole = (MASConsole) Class.forName(consoleClass).newInstance();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			if(masConsole == null) {
				masConsole = new TerminalMASConsole();
			}
		}
		
		return masConsole;
	}
	
	public static boolean hasConsole() {
        return masConsole != null;
    }
	
	private OutputStreamAdapter out;
    private boolean             inPause = false;
    
    public MASConsole() {
    	
    }
    
    public MASConsole(String title) {
		
	}
    
    public void setTitle(String title) {
    	
    }
    
    synchronized public void setPause(boolean b) {
        inPause = b;
        notifyAll();
    }

    synchronized void waitNotPause() {
        try {
            while (inPause) {
                wait();
            }
        } catch (Exception e) { }
    }

    public boolean isPause() {
        return inPause;
    }

    public void append(String s) {
        append(null, s);
    }

    public abstract void append(String agName, String s);
    
    public abstract void close();

    public void setAsDefaultOut() {
        out = new OutputStreamAdapter(this);
        out.setAsDefaultOut();
    }
}
