package jason.runtime.console;

public class TerminalMASConsole extends MASConsole {
	
	
	public TerminalMASConsole() {
		
	}

	@Override
	public void append(String agName, String s) {
		System.out.printf(s);

	}

	@Override
	public void close() {
		
	}

	@Override
	public void setAsDefaultOut() {
		
	}
}
