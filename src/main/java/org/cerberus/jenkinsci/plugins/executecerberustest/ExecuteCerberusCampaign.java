/**
 * Cerberus Copyright (C) 2013 - 2017 cerberustesting
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Cerberus.
 *
 * Cerberus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cerberus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cerberus.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cerberus.jenkinsci.plugins.executecerberustest;
import org.apache.commons.lang.StringUtils;
import org.cerberus.launchcampaign.checkcampaign.*;
import org.cerberus.launchcampaign.event.LogEvent;
import org.cerberus.launchcampaign.executecampaign.*;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;

import hudson.*;
import hudson.model.*;
import hudson.tasks.*;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

/**
 * ExecuteCerberusCampaign {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link ExecuteCerberusCampaign} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #campaignName})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Nicolas Deblock
 */
public class ExecuteCerberusCampaign extends Builder implements SimpleBuildStep {

	private final String campaignName;
	private final String platform;        
	private final String environment;
	private final String browser;
	private final String browserVersion;
	private final String ssIp;
	private final String ss_p;
	private final String screensize;
	private final String robot;
	    
	private final int screenshot; // default is 1
	private final int verbose; // default is 1       
	private final int pageSource; // default is Y
	private final int seleniumLog; // default is Y
	private final int timeOut; // default is 5000
	private final int retries; // default is 0
	private final String tag; // default is 'Jenkins--' + current timestamp
		
	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public ExecuteCerberusCampaign(final String campaignName, final String platform, final String environment, final String browser, 
			final String browserVersion, final int screenshot, final int verbose, final int pageSource, final int seleniumLog, 
			final int timeOut, final int retries, String tag, final String ss_p, final String screensize, final String ssIp, final String robot) {
		this.campaignName = campaignName;
		this.platform=platform;
		this.environment=environment;
		this.browser =  browser; 
		this.browserVersion = browserVersion;
		
		this.screenshot = screenshot; // default is 1
		this.verbose = verbose; // default is 1       
		this.pageSource = pageSource; // default is Y
		this.seleniumLog = seleniumLog; // default is Y
		this.timeOut = timeOut; // default is 5000
		this.retries = retries; // default is 0
		this.tag = tag; 
		
		this.ss_p =ss_p;
		this.ssIp =ssIp;		
		this.robot =robot;
		this.screensize =screensize;
	}

	@Override
	public void perform(final Run<?,?> build, final FilePath workspace, final Launcher launcher, final TaskListener listener) {
		final JenkinsLogger logger = new JenkinsLogger(listener.getLogger());

		// overide attribute if local settings is empty
	    final String robot =   StringUtils.isEmpty(this.robot) ? getDescriptor().getRobot() : this.robot; 
	      
		final String ssIp =   StringUtils.isEmpty(this.ssIp) ? getDescriptor().getSsIp() : this.ssIp; 
		final String ss_p =   StringUtils.isEmpty(this.ss_p) ? getDescriptor().getSs_p() : this.ss_p;
		final String platform =   StringUtils.isEmpty(this.platform) ? getDescriptor().getPlatform() : this.platform; 		
		final String browser =   StringUtils.isEmpty(this.browser) ? getDescriptor().getBrowser() : this.browser; 
		final String browserVersion = StringUtils.isEmpty(this.browserVersion) ? getDescriptor().getBrowserVersion() : this.browserVersion;

		
		try {
			// calucation of tag
	        EnvVars env = build.getEnvironment(listener); 
	        final String expandedTag = env.expand(this.tag); 
			
			// 1 - Launch cerberus campaign    		
			final ExecuteCampaignDto executeCampaignDto = new ExecuteCampaignDto(robot, ssIp, 
					environment, browser, browserVersion, platform, campaignName, screenshot, verbose, pageSource, 
					seleniumLog, timeOut, retries, expandedTag, ss_p, screensize);
		
			logger.info("Launch campaign " + executeCampaignDto.getSelectedCampaign() + " on " + getDescriptor().getUrlCerberus() + " with tag " + executeCampaignDto.getTagCerberus());

			String urlCerberusReport = getDescriptor().getUrlCerberus() + "/ReportingExecutionByTag.jsp?Tag=" + executeCampaignDto.getTagCerberus();
			
			LogEvent logEvent= new LogEvent() {				
				@Override
				public void log(String error, String warning) {
					if(!StringUtils.isEmpty(warning)) {
						logger.warning(warning);
					}
					if(!StringUtils.isEmpty(error)) {
						logger.error(error);
					}
					
				}
			};
			
            final ExecuteCampaign executeCampaign = new ExecuteCampaign(getDescriptor().getUrlCerberus(), executeCampaignDto);
			if(executeCampaign.execute(logEvent)) {
				// 2 - check if cerberus campaign is finish
				logger.info("Campaign is launched successfully. You can follow the report here : " +  urlCerberusReport);
				
				CheckCampaignStatus checkCampaignStatus = new CheckCampaignStatus(executeCampaignDto.getTagCerberus(), getDescriptor().getUrlCerberus(), getDescriptor().timeToRefreshCheckCampaignStatus, getDescriptor().timeOutForCampaignExecution);
				checkCampaignStatus.execute(new CheckCampaignStatus.CheckCampaignEvent() {
					
					@Override
					public boolean checkCampaign(final ResultCIDto resultDto) {
						logger.info(resultDto.getTotalTestExecuted() + " test executed ... ("+ resultDto.logDetailExecution() + ")");
						logger.info(resultDto.getStatusPE() + resultDto.getStatusNE() + " test pending ...");
						logger.info("cerberus message : " + resultDto.getMessage());
						logger.info("Advancement : " + resultDto.getPercentOfTestExecuted() + "%");
						return true;
					}
				}, new CheckCampaignStatus.ResultEvent() {
					
					@Override
					public void result(final ResultCIDto resultDto) {
						// display result and shutdown
						long timeToExecuteTest = resultDto.getExecutionEnd().getTime() - resultDto.getExecutionStart().getTime();
						logger.info("---------------------------------------------------------------------------------------------");
						logger.info("Result : " + resultDto.getResult() + ", test executed in " + ((int)(timeToExecuteTest/1000)) + "s " + ((int)(timeToExecuteTest%1000)) + "ms");
						logger.info(resultDto.logDetailExecution());		    							    					
						logger.info("---------------------------------------------------------------------------------------------");
	
						// fail if test is not OK
						if(!"OK".equals(resultDto.getResult())) {
							logger.error("FAIL");
							build.setResult(Result.UNSTABLE);
						}
					}
				},logEvent);
				
				logger.info("Campaign execution is finished. You can view the report here : " +  urlCerberusReport);
			} else {
				logger.error("Fail to add campaign " + campaignName + " in cerberus queue");
				logger.error("Think to check cerberus log or cerberus queue to resolve problem");
				logger.error("UNSTABLE");
				build.setResult(Result.FAILURE);
			}
		} catch (Exception e) {
			logger.error("error for campaign  " + campaignName + " : " , e);
			logger.error("Think to check cerberus log or cerberus queue to resolve problem");
			logger.error("UNSTABLE");
			build.setResult(Result.FAILURE);
		} 
	}
	
	
	public String getCampaignName() {
		return campaignName;
	}

	
	public String getPlatform() {
		return platform;
	}

	public String getEnvironment() {
		return environment;
	}

	public String getBrowser() {
		return browser;
	}

	public String getBrowserVersion() {
		return browserVersion;
	}

	public int getScreenshot() {
		return screenshot;
	}

	public int getVerbose() {
		return verbose;
	}

	public int getPageSource() {
		return pageSource;
	}

	public int getSeleniumLog() {
		return seleniumLog;
	}

	public int getTimeOut() {
		return timeOut;
	}

	public int getRetries() {
		return retries;
	}

	public String getTag() {
		return tag;
	}

	public String getSs_p() {
        return ss_p;
    }

    public String getScreensize() {
        return screensize;
    }

    public String getSsIp() {
        return ssIp;
    }

    public String getRobot() {
        return robot;
    }

    // Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link ExecuteCerberusCampaign}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See {@code src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly}
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Symbol("executeCerberusCampaign")
	@Extension // This indicates to Jenkins that this is an implementation of an extension point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use {@code transient}.
		 */
		private String urlCerberus;
		private String robot;
		private String ssIp;
		private String ss_p;
		private String platform;
		private String browser;
		private String browserVersion;
		private long timeToRefreshCheckCampaignStatus;
		private int timeOutForCampaignExecution;


		/**
		 * In order to load the persisted global configuration, you have to 
		 * call load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * In order to load the persisted global configuration, you have to 
		 * call load() in the constructor.
		 */
		public DescriptorImpl(boolean fortest) {

		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types 
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return "Execute Cerberus Campaign";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			urlCerberus = formData.getString("urlCerberus");
			robot = formData.getString("robot");
			ssIp = formData.getString("ssIp");
			ss_p = formData.getString("ss_p");  
			browser = formData.getString("browser");
			browserVersion = formData.getString("browserVersion");
			timeToRefreshCheckCampaignStatus = formData.getLong("timeToRefreshCheckCampaignStatus");
			timeOutForCampaignExecution = formData.getInt("timeOutForCampaignExecution");
			platform = formData.getString("platform");
			// Can also use req.bindJSON(this, formData);
			//  (easier when there are many fields; need set* methods for this)
			save();
			return super.configure(req,formData);
		}

		public String getUrlCerberus() {
			return urlCerberus;
		}      
		public void setUrlCerberus(String url) {
			this.urlCerberus=url;
		}

		public String getRobot() {
			return robot;
		}

		public void setRobot(String robot) {
			this.robot = robot;
		}

		public String getSsIp() {
			return ssIp;
		}

		public void setSsIp(String ssIp) {
			this.ssIp = ssIp;
		}

		public String getBrowser() {
			return browser;
		}

		public void setBrowser(String browser) {
			this.browser = browser;
		}

		public String getBrowserVersion() {
			return browserVersion;
		}

		public void setBrowserVersion(String browserVersion) {
			this.browserVersion = browserVersion;
		}

		public long getTimeToRefreshCheckCampaignStatus() {
			return timeToRefreshCheckCampaignStatus;
		}

		public void setTimeToRefreshCheckCampaignStatus(long timeToRefreshCheckCampaignStatus) {
			this.timeToRefreshCheckCampaignStatus = timeToRefreshCheckCampaignStatus;
		}

		public int getTimeOutForCampaignExecution() {
			return timeOutForCampaignExecution;
		}

		public void setTimeOutForCampaignExecution(int timeOutForCampaignExecution) {
			this.timeOutForCampaignExecution = timeOutForCampaignExecution;
		}

        public String getSs_p() {
            return ss_p;
        }

        public void setSs_p(String ss_p) {
            this.ss_p = ss_p;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

	}

}

