package org.metricssampler.daemon;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.metricssampler.config.Configuration;
import org.metricssampler.config.SamplerConfig;
import org.metricssampler.sampler.Sampler;
import org.metricssampler.service.Bootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Daemon {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Bootstrapper bootstrapper;

	private ScheduledThreadPoolExecutor executor;
	private Thread controllerThread;
	private final Map<String, SamplerTask> tasks = new HashMap<String, SamplerTask>();
	
	public Daemon(final Bootstrapper bootstrapper) {
		this.bootstrapper = bootstrapper;
	}

	/**
	 * The order of operations is significant here: 
	 * <ol>
	 * <li>Setup the thread pool</li>
	 * <li>Create the controller which makes sure there is no other process running on the
	 * local machine because it would otherwise fail to bind the server socket</li>
	 * <li>Schedule the samplers</li>
	 * <li>Startup the controller thread. From this point it can really process requests</li>
	 * </ol>
	 */
	public void start() {
		executor = setupThreadPool();
		createController();
		scheduleSamplers();
		controllerThread.start();
	}

	private void createController() {
		try {
			final Runnable controller = new TCPController(bootstrapper, executor, tasks);
			controllerThread = new Thread(controller);
		} catch (final IllegalStateException e) {
			logger.error(e.getMessage(), e);
			System.exit(1);
		}
	}

	private ScheduledThreadPoolExecutor setupThreadPool() {
		final Configuration config = bootstrapper.getConfiguration();
		logger.info("Starting thread pool executor with thread pool size: " + config.getPoolSize());
		return new ScheduledThreadPoolExecutor(config.getPoolSize());
	}

	private void scheduleSamplers() {
		for (final Sampler sampler : bootstrapper.getSamplers()) {
			final SamplerConfig config = sampler.getConfig();
			logger.info("Scheduling {} at fixed rate of {} seconds", sampler, config.getInterval());
			final SamplerTask task = new SamplerTask(sampler);
			if (config.isDisabled()) {
				task.disable();
			}
			tasks.put(config.getName(), task);
			executor.scheduleAtFixedRate(task, 0L, config.getInterval(), TimeUnit.SECONDS);
		}
	}
}
