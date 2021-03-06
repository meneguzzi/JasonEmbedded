//----------------------------------------------------------------------------
// Copyright (C) 2003  Rafael H. Bordini, Jomi F. Hubner, et al.
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
import jason.asSemantics.ActionExec;
import jason.asSyntax.Structure;
import jason.environment.Environment;
import jason.environment.EnvironmentInfraTier;
import jason.mas2j.ClassParameters;
import jason.runtime.RuntimeServicesInfraTier;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class implements the centralised version of the environment infrastructure tier.
 */
public class EmbeddedEnvironment implements EnvironmentInfraTier {

    /** the user customisation class for the environment */
	private Environment userEnv;
	private RunEmbeddedMAS masRunner = RunEmbeddedMAS.getRunner();
	private boolean running = true;
    
    private static Logger logger = Logger.getLogger(EmbeddedEnvironment.class.getName());
	
    public EmbeddedEnvironment(ClassParameters userEnvArgs, RunEmbeddedMAS masRunner) throws JasonException {
        this.masRunner = masRunner;
        if (userEnvArgs != null) {
            try {
    			userEnv = (Environment) getClass().getClassLoader().loadClass(userEnvArgs.getClassName()).newInstance();
    			userEnv.setEnvironmentInfraTier(this);
    			userEnv.init(userEnvArgs.getParametersArray());
            } catch (Exception e) {
                logger.log(Level.SEVERE,"Error in Centralised MAS environment creation",e);
                throw new JasonException("The user environment class instantiation '"+userEnvArgs+"' has failed!"+e.getMessage());
            }
        }
    }
	
    public boolean isRunning() {
        return running;
    }
    
	/** called before the end of MAS execution, it just calls the user environment class stop method. */
	public void stop() {
        running = false;
		userEnv.stop();
	}

	public void setUserEnvironment(Environment env) {
	    userEnv = env;
	}
    public Environment getUserEnvironment() {
        return userEnv;
    }

    /** called by the agent infra arch to perform an action in the environment */
    public void act(final String agName, final ActionExec action) {
    	if (running) {
            userEnv.scheduleAction(agName, action.getActionTerm(), action);
        }
    }
    
    public void actionExecuted(String agName, Structure actTerm, boolean success, Object infraData) {
        ActionExec action = (ActionExec)infraData;
        if (success) {
            action.setResult(true);
        } else {
            action.setResult(false);
        }
        masRunner.getAg(agName).actionExecuted(action);
    }
    
    
    public void informAgsEnvironmentChanged() {
        for (EmbeddedAgArch ag: masRunner.getAgs().values()) {
            ag.wake();
        }
    }

    public void informAgsEnvironmentChanged(Collection<String> agentsToNotify) {
        if (agentsToNotify == null) {
        	for (EmbeddedAgArch ag: masRunner.getAgs().values()) {
                ag.wake();
            }
        } else {
            for (String agName: agentsToNotify) {
            	EmbeddedAgArch ag = masRunner.getAg(agName);
                if (ag != null) {
                    ag.wake();
                } else {
                    logger.log(Level.SEVERE, "Error sending message notification: agent " + agName + " does not exist!");
                }
            }
        }
    }

    public RuntimeServicesInfraTier getRuntimeServices() {
        return new EmbeddedRuntimeServices(masRunner);
    }

	/* (non-Javadoc)
	 * @see jason.environment.EnvironmentInfraTier#informAgsEnvironmentChanged(java.lang.String[])
	 */
	public void informAgsEnvironmentChanged(String... agents) {
		if (agents == null) {
        	for (EmbeddedAgArch ag: masRunner.getAgs().values()) {
                ag.wake();
            }
        } else {
            for (String agName: agents) {
            	EmbeddedAgArch ag = masRunner.getAg(agName);
                if (ag != null) {
                    ag.wake();
                } else {
                    logger.log(Level.SEVERE, "Error sending message notification: agent " + agName + " does not exist!");
                }
            }
        }
	}
}
