package jason.infra.embedded;

import jason.infra.InfrastructureFactory;
import jason.jeditplugin.MASLauncherInfraTier;
import jason.runtime.RuntimeServicesInfraTier;

public class EmbeddedFactory implements InfrastructureFactory {

	public MASLauncherInfraTier createMASLauncher() {
		return new EmbeddedMASLauncherAnt();
	}
	
	public RuntimeServicesInfraTier createRuntimeServices() {
		return new EmbeddedRuntimeServices(RunEmbeddedMAS.getRunner());
	}

}
