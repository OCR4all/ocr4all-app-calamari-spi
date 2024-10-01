/**
 * File:     CalamariServiceProviderProcessorWorker.java
 * Package:  de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core
 * 
 * Author:   Herbert Baier (herbert.baier@uni-wuerzburg.de)
 * Date:     13.06.2024
 */
package de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core;

import java.security.ProviderException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.ProcessRequest;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.core.Batch;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.core.BatchArgument;
import de.uniwuerzburg.zpd.ocr4all.application.communication.message.spi.EventSPI;
import de.uniwuerzburg.zpd.ocr4all.application.communication.msa.api.domain.JobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.communication.msa.api.domain.SystemJobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.MsaProcessorServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider.Processor;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Dataset;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.Argument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.WeightArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.SystemProcess;

/**
 * Defines Calamari service provider processor workers. The following properties of the
 * service provider collection <b>calamari</b> override the local default
 * settings (<b>key</b>: <i>default value</i>):
 * <ul>
 * <li>msa-timeout-active-processor: 15000</li>
 * <li>see {@link CalamariServiceProviderWorker} for remainder settings</li>
 * </ul>
 * 
 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
 * @version 1.0
 * @since 17
 */
public abstract class CalamariServiceProviderProcessorWorker<C extends ProcessorCore.Callback, F extends Framework, P extends ProcessRequest, J extends JobResponse>
		extends CalamariServiceProviderWorker {
	/**
	 * Defines service provider collection with keys and default values. Collection
	 * blank values are not allowed and their values are trimmed.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	private enum ServiceProviderCollection implements ConfigurationServiceProvider.CollectionKey {
		timeoutActiveProcessor("msa-timeout-active-processor", "15000");

		/**
		 * The key.
		 */
		private final String key;

		/**
		 * The default value.
		 */
		private final String defaultValue;

		/**
		 * Creates a service provider collection with a key and default value.
		 * 
		 * @param key          The key.
		 * @param defaultValue The default value.
		 * @since 1.8
		 */
		private ServiceProviderCollection(String key, String defaultValue) {
			this.key = key;
			this.defaultValue = defaultValue;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.env.ProcessFramework.
		 * ServiceProviderCollectionKey#getName()
		 */
		@Override
		public String getName() {
			return collectionName;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.env.ProcessFramework.
		 * ServiceProviderCollectionKey#getKey()
		 */
		@Override
		public String getKey() {
			return key;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.env.ProcessFramework.
		 * ServiceProviderCollectionKey#getDefaultValue()
		 */
		@Override
		public String getDefaultValue() {
			return defaultValue;
		}

	}

	/**
	 * The job request mapping.
	 */
	public static final String jobRequestMapping = schedulerControllerContextPath + "job/{id}";

	/**
	 * The expunge job request mapping.
	 */
	public static final String expungeJobRequestMapping = schedulerControllerContextPath + "expunge/{id}";

	/**
	 * The cancel job request mapping.
	 */
	public static final String cancelJobRequestMapping = "/cancel/{id}";

	/**
	 * The job type.
	 */
	private final Class<J> jobType;;

	/**
	 * The system job request mapping.
	 */
	protected final String systemJobRequestMapping;

	/**
	 * The timeout for the active processor.
	 */
	protected final long timeoutActiveProcessor;

	/**
	 * Default constructor for a calamari service provider worker.
	 * 
	 * @param logger The logger class.
	 * @param type   The type.
	 * @since 17
	 */
	public CalamariServiceProviderProcessorWorker(Class<?> logger, Type type, Class<J> jobType) {
		super(logger, type);

		this.jobType = jobType;

		systemJobRequestMapping = apiContextPathVersion_1_0 + type.name() + "/job/{id}";

		long timeoutActiveProcessor;
		try {
			timeoutActiveProcessor = Long
					.parseLong(configuration.getValue(ServiceProviderCollection.timeoutActiveProcessor));
		} catch (Exception e) {
			timeoutActiveProcessor = Long.parseLong(ServiceProviderCollection.timeoutActiveProcessor.getDefaultValue());
		}

		this.timeoutActiveProcessor = timeoutActiveProcessor > 0 ? timeoutActiveProcessor : 0;
	}

	/**
	 * Returns the batch recognition model arguments.
	 * 
	 * @param arguments The arguments for the spi model.
	 * @return The batch recognition model arguments.
	 * @since 17
	 */
	protected List<BatchArgument> getBatchRecognitionModelArguments(List<Argument> arguments) {
		List<BatchArgument> list = new ArrayList<>();

		if (arguments != null)
			for (Argument argument : arguments)
				if (argument instanceof WeightArgument recognitionModel) {
					if (recognitionModel.getAssembles().isPresent()) {
						List<Batch.Item> items = new ArrayList<>();

						for (WeightArgument.Assemble assemble : recognitionModel.getAssembles().get())
							if (assemble != null) {
								List<String> files = new ArrayList<>();

								for (String model : assemble.getModels())
									if (model != null && !model.isBlank())
										files.add(model.trim());

								if (!files.isEmpty())
									items.add(new Batch.Item(assemble.getId(), files));
							}

						if (!items.isEmpty())
							list.add(new BatchArgument(recognitionModel.getArgument(), items));
					}
				}

		return list;
	}

	/**
	 * Returns the batch for recognition model arguments.
	 * 
	 * @param dataset The dataset.
	 * @return The batch for recognition model arguments. Null if the dataset is
	 *         empty.
	 * @since 17
	 */
	protected Batch getBatch(Dataset dataset) {
		List<Batch.Item> items = new ArrayList<>();

		if (dataset != null && dataset.getCollections() != null)
			for (Dataset.Collection collection : dataset.getCollections()) {
				if (collection != null && collection.getId() != null && !collection.getId().isBlank()
						&& collection.getSets() != null) {
					List<String> files = new ArrayList<>();

					for (Dataset.Collection.Set set : collection.getSets())
						if (set != null && set.getId() != null && !set.getId().isBlank() && set.getImage() != null
								&& !set.getImage().isBlank())
							files.add(set.getId().trim() + "." + set.getImage().trim());

					if (!files.isEmpty())
						items.add(new Batch.Item(collection.getId().trim(), files));
				}

			}
		return items.isEmpty() ? null : new Batch(items);
	}

	/**
	 * Returns the process request.
	 * 
	 * @param key           The job key.
	 * @param framework     The framework for the processor.
	 * @param modelArgument The models with their arguments.
	 * @return The process requests.
	 * @since 17
	 */
	protected abstract P getProcessRequest(String key, F framework, ModelArgument modelArgument);

	/**
	 * Override this method to perform the desired logic before the process start
	 * execution.
	 * 
	 * @param framework      The framework for the processor.
	 * @param modelArgument  The models with their arguments.
	 * @param processRequest The process request.
	 * @throws Exception Thrown on conditions that the calling application catch and
	 *                   logs.
	 * @since 17
	 */
	protected void preExecuteCallback(F framework, ModelArgument modelArgument, P processRequest) throws Exception {
	}

	/**
	 * Performs the desired logic before the process start execution.
	 * 
	 * @param framework      The framework for the processor.
	 * @param modelArgument  The models with their arguments.
	 * @param processRequest The process request.
	 * @param logTrouble     The callback method to log troubles.
	 * @since 17
	 */
	private void preExecuteCallback(F framework, ModelArgument modelArgument, P processRequest,
			LogCallback logTrouble) {
		try {
			preExecuteCallback(framework, modelArgument, processRequest);
		} catch (Exception e) {
			String message = getProcessorIdentifier() + ": pre execute callback performs with troubles - "
					+ e.getMessage();

			logger.warn(message);
			logTrouble.log(message);
		}
	}

	/**
	 * Override this method to perform the desired logic after the process start
	 * execution.
	 * 
	 * @param job           The job.
	 * @param framework     The framework for the processor.
	 * @param modelArgument The models with their arguments.
	 * @return The job response.
	 * @throws Exception Thrown on conditions that the calling application catch and
	 *                   logs.
	 * @since 17
	 */
	protected JobResponse posExecuteCallback(J job, F framework, ModelArgument modelArgument) throws Exception {
		return job;
	}

	/**
	 * Performs the desired logic after the process start execution.
	 * 
	 * @param job           The job.
	 * @param framework     The framework for the processor.
	 * @param modelArgument The models with their arguments.
	 * @param logTrouble    The callback method to log troubles.
	 * @return The job response.
	 * @since 17
	 */
	private JobResponse posExecuteCallback(J job, F framework, ModelArgument modelArgument, LogCallback logTrouble) {
		JobResponse processJob = job;
		try {
			processJob = posExecuteCallback(job, framework, modelArgument);
		} catch (Exception e) {
			String message = getProcessorIdentifier() + ": post execute callback performs with troubles - "
					+ e.getMessage();

			logger.warn(message);
			logTrouble.log(message);
		}
		return processJob;
	}

	/**
	 * Override this method to perform the desired logic after the process is
	 * complete.
	 * 
	 * @param state         The process finish state.
	 * @param framework     The framework for the processor.
	 * @param modelArgument The models with their arguments.
	 * @return The process state.
	 * @throws Exception Thrown on conditions that the calling application catch and
	 *                   logs.
	 * @since 17
	 */
	protected ProcessorCore.State postProcessingCallback(ProcessorCore.State state, F framework,
			ModelArgument modelArgument) throws Exception {
		return state;
	}

	/**
	 * Performs the desired logic after the process is complete.
	 * 
	 * @param state         The process finish state.
	 * @param framework     The framework for the processor.
	 * @param modelArgument The models with their arguments.
	 * @param logTrouble    The callback method to log troubles.
	 * @return The process state.
	 * @since 17
	 */
	private ProcessorCore.State postProcessingCallback(
			de.uniwuerzburg.zpd.ocr4all.application.communication.msa.job.State state, F framework,
			ModelArgument modelArgument, LogCallback logTrouble) {
		ProcessorCore.State processState;

		switch (state) {
		case canceled:
			processState = ProcessorCore.State.canceled;
			break;
		case completed:
			processState = ProcessorCore.State.completed;
			break;
		case interrupted:
		default:
			processState = ProcessorCore.State.interrupted;
		}

		try {
			processState = postProcessingCallback(processState, framework, modelArgument);
		} catch (Exception e) {
			String message = getProcessorIdentifier() + ": post processing callback performs with troubles - "
					+ e.getMessage();

			logger.warn(message);
			logTrouble.log(message);
		}

		if (processState == null)
			return ProcessorCore.State.interrupted;
		else
			switch (processState) {
			case canceled:
			case completed:
				return processState;
			case interrupted:
			default:
				return ProcessorCore.State.interrupted;
			}
	}

	/**
	 * Defines callback for log.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 17
	 */
	@FunctionalInterface
	private interface LogCallback {
		/**
		 * Logs the message.
		 * 
		 * @param message The message.
		 * @since 17
		 */
		public void log(String message);
	}

	/**
	 * Returns a new processor for the Calamari service provider.
	 * 
	 * @return A new processor for the Calamari service provider.
	 * @since 17
	 */
	protected Processor<C, F> newCalamaryProcessor() {
		return providerDescription == null ? null
				: new MsaProcessorServiceProvider<C, F>(microserviceArchitecture.getEventController()) {
					/**
					 * The timeout thread.
					 */
					private Thread thread = null;

					/**
					 * The job id. 0 if not set.
					 */
					private int jobId = 0;

					/**
					 * Logs the trouble.
					 * 
					 * @param message The trouble message.
					 * @since 17
					 */
					private void logTrouble(String message) {
						logger.warn(getProcessorIdentifier() + ": " + message);

						updatedStandardError(message);
					}

					/*
					 * (non-Javadoc)
					 * 
					 * @see de.uniwuerzburg.zpd.ocr4all.application.ocrd.spi.msa.
					 * OCRDMsaProcessorServiceProvider#handle(de.uniwuerzburg.zpd.ocr4all.
					 * application.communication.message.spi.EventSPI)
					 */
					@Override
					protected void handle(EventSPI event) {
						final String message = "received event " + event.getType().name() + " (" + event.getCreatedAt()
								+ ") - " + event.getMessage().getContent();

						logger.debug(getProcessorIdentifier() + ": " + message);

						if (event.getType().equals(EventSPI.Type.interrupted))
							updatedStandardError(message);
						else
							updatedStandardOutput(message);

						if (thread != null && thread.isAlive() && !thread.isInterrupted())
							thread.interrupt();
					}

					/*
					 * (non-Javadoc)
					 * 
					 * @see
					 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider.
					 * Processor#execute(de.uniwuerzburg.zpd.ocr4all.application.spi.core.
					 * ProcessorCore.Callback,
					 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework,
					 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
					 */
					@Override
					public ProcessorCore.State execute(C callback, F framework, ModelArgument modelArgument) {
						callback.updatedProgress(0.01F);

						if (framework == null) {
							updatedStandardError("undefined framework.");

							return ProcessorCore.State.interrupted;
						}

						try {
							ping();
						} catch (ProviderException e) {
							logTrouble("trouble contacting Calamari msa - " + e.getMessage());

							return ProcessorCore.State.interrupted;
						}

						if (!initialize(getProcessorIdentifier(), callback, framework))
							return ProcessorCore.State.canceled;

						// register event handler
						registerEventHandler();

						P processRequest = getProcessRequest(key, framework, modelArgument);

						logger.debug(getProcessorIdentifier() + ": process request - key " + key + ", arguments '"
								+ processRequest.getArguments() + "'.");

						callback.updatedProgress(0.02F);

						preExecuteCallback(framework, modelArgument, processRequest, message -> logTrouble(message));

						callback.updatedProgress(0.05F);

						// start the job
						JobResponse jobResponse;
						try {
							jobResponse = posExecuteCallback(
									restClient.post().uri(executeRequestMapping).contentType(MediaType.APPLICATION_JSON)
											.body(processRequest).accept(MediaType.APPLICATION_JSON).retrieve()
											.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
												throw new ProviderException("HTTP client error status "
														+ response.getStatusCode() + " (" + response.getStatusText()
														+ "): " + response.getHeaders());
											}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
												throw new ProviderException("HTTP server error status "
														+ response.getStatusCode() + " (" + response.getStatusText()
														+ "): " + response.getHeaders());
											}).body(jobType),
									framework, modelArgument, message -> logTrouble(message));
						} catch (Exception e) {
							logTrouble("could not execute processor, key " + key + " - " + e.getMessage());

							return ProcessorCore.State.interrupted;
						}

						jobId = jobResponse.getId();

						logger.debug(getProcessorIdentifier() + ": running job " + jobId + ", key " + key + ".");

						// wait until the job is done
						while (!jobResponse.getState().isDone()) {
							thread = new Thread(() -> {
								try {
									logger.debug("thread wait: job " + jobId + ", key " + key + ".");

									Thread.sleep(timeoutActiveProcessor);

									logger.debug("thread timeout: job " + jobId + ", key " + key + ".");
								} catch (InterruptedException e) {
									logger.debug("thread interrupted by event: job " + jobId + ", key " + key + ".");
								}
							});

							thread.start();

							// Wait for a timeout or a new event
							try {
								thread.join();
							} catch (InterruptedException e) {
								// Nothing to do
							}

							// restore the current job status
							try {
								jobResponse = restClient.get().uri(jobRequestMapping, jobId)
										.accept(MediaType.APPLICATION_JSON).retrieve()
										.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
											throw new ProviderException(
													"HTTP client error status " + response.getStatusCode() + " ("
															+ response.getStatusText() + "): " + response.getHeaders());
										}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
											throw new ProviderException(
													"HTTP server error status " + response.getStatusCode() + " ("
															+ response.getStatusText() + "): " + response.getHeaders());
										}).body(JobResponse.class);
							} catch (Exception e) {
								logTrouble(
										"could not restore the job " + jobId + ", key " + key + " - " + e.getMessage());

								return ProcessorCore.State.interrupted;
							}
						}

						// job is done, unregister event handler
						unregisterEventHandler();

						// restore the system job information
						try {
							callback.updatedProgress(0.98F);
							SystemJobResponse systemJobResponse = restClient.get().uri(systemJobRequestMapping, jobId)
									.accept(MediaType.APPLICATION_JSON).retrieve()
									.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
										throw new ProviderException(
												"HTTP client error status " + response.getStatusCode() + " ("
														+ response.getStatusText() + "): " + response.getHeaders());
									}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
										throw new ProviderException(
												"HTTP server error status " + response.getStatusCode() + " ("
														+ response.getStatusText() + "): " + response.getHeaders());
									}).body(SystemJobResponse.class);

							if (systemJobResponse.getStandardOutput() != null
									&& !systemJobResponse.getStandardOutput().isBlank())
								updatedStandardOutput(systemJobResponse.getStandardOutput());

							if (systemJobResponse.getStandardError() != null
									&& !systemJobResponse.getStandardError().isBlank())
								updatedStandardError(systemJobResponse.getStandardError());

							if (systemJobResponse.getExitValue() > 0)
								updatedStandardError("processor exit code: " + systemJobResponse.getExitValue());

							callback.updatedProgress(0.99F);
							try {
								restClient.get().uri(expungeJobRequestMapping, jobId).retrieve()
										.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
											throw new ProviderException(
													"HTTP client error status " + response.getStatusCode() + " ("
															+ response.getStatusText() + "): " + response.getHeaders());
										}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
											throw new ProviderException(
													"HTTP server error status " + response.getStatusCode() + " ("
															+ response.getStatusText() + "): " + response.getHeaders());
										}).toBodilessEntity();

							} catch (Exception e) {
								logTrouble(
										"could not expunge the job " + jobId + ", key " + key + " - " + e.getMessage());
							}

							return postProcessingCallback(systemJobResponse.getState(), framework, modelArgument,
									message -> logTrouble(message));
						} catch (Exception e) {
							logTrouble("could not restore system information of the job " + jobId + ", key " + key
									+ " - " + e.getMessage());

							return postProcessingCallback(jobResponse.getState(), framework, modelArgument,
									message -> logTrouble(message));
						}
					}

					/*
					 * (non-Javadoc)
					 * 
					 * @see
					 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.CoreProcessorServiceProvider
					 * #cancel()
					 */
					@Override
					public void cancel() {
						super.cancel();

						if (jobId > 0)
							try {
								restClient.get().uri(cancelJobRequestMapping, jobId).retrieve()
										.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
											throw new ProviderException(
													"HTTP client error status " + response.getStatusCode() + " ("
															+ response.getStatusText() + "): " + response.getHeaders());
										}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
											throw new ProviderException(
													"HTTP server error status " + response.getStatusCode() + " ("
															+ response.getStatusText() + "): " + response.getHeaders());
										}).toBodilessEntity();

							} catch (Exception e) {
								logTrouble(
										"could not cancel the job " + jobId + ", key " + key + " - " + e.getMessage());
							}

					}
				};

	}

	/**
	 * Defines callback for messages.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	@FunctionalInterface
	protected interface Message {
		/**
		 * Updates the message.
		 * 
		 * @param content The message content.
		 * @since 1.8
		 */
		public void update(String content);
	}

	/**
	 * Updates the processor messages.
	 * 
	 * @param process        The system process.
	 * @param standardOutput The callback for standard output.
	 * @param standardError  The callback for standard error.
	 * @since 1.8
	 */
	protected static void updateProcessorMessages(SystemProcess process, Message standardOutput,
			Message standardError) {
		if (process != null) {
			String message = process.getStandardOutput();
			if (!message.isBlank())
				standardOutput.update(message.trim());

			message = process.getStandardError();
			if (!message.isBlank())
				standardError.update(message.trim());
		}
	}

	/**
	 * Defines callback for progresses.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	@FunctionalInterface
	protected interface Progress {
		/**
		 * Updates the progress.
		 * 
		 * @param value The progress value.
		 * @since 1.8
		 */
		public void update(float value);
	}

	/**
	 * Defines callback for processor running state.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	@FunctionalInterface
	protected interface ProcessorRunningState {
		/**
		 * Returns true if the processor was canceled.
		 * 
		 * @return True if the processor was canceled.
		 * @since 1.8
		 */
		public boolean isCanceled();
	}

	/**
	 * Defines callback for processor execution.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	@FunctionalInterface
	protected interface ProcessorExecution {
		/**
		 * Completes the execution of the processor.
		 * 
		 * @return The process execution state.
		 * @since 1.8
		 */
		public ProcessorServiceProvider.Processor.State complete();
	}

}
