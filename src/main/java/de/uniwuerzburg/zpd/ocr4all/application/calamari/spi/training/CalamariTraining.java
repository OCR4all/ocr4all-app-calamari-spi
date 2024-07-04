/**
 * File:     CalamariTraining.java
 * Package:  de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.training
 * 
 * Author:   Herbert Baier (herbert.baier@uni-wuerzburg.de)
 * Date:     26.06.2024
 */
package de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.training;

import java.security.ProviderException;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.TrainingRequest;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.core.ModelConfiguration;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.CalamariServiceProviderWorker;
import de.uniwuerzburg.zpd.ocr4all.application.communication.message.spi.EventSPI;
import de.uniwuerzburg.zpd.ocr4all.application.communication.msa.api.domain.JobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.communication.msa.api.domain.SystemJobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.spi.TrainingServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.MsaProcessorServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.CollectionKey;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.TrainingFramework;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument;

/**
 * Defines service providers for the Calamari training processor. The following
 * properties of the service provider collection <b>calamari</b> override the
 * local default settings (<b>key</b>: <i>default value</i>):
 * <ul>
 * <li>training-id: training</li>
 * <li>training-description: Calamari training processor</li>
 * <li>see {@link CalamariServiceProviderWorker} for remainder settings</li>
 * </ul>
 *
 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
 * @version 1.0
 * @since 17
 */
public class CalamariTraining extends CalamariServiceProviderWorker implements TrainingServiceProvider {

	/**
	 * Defines service provider collection with keys and default values. Collection
	 * blank values are not allowed and their values are trimmed.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	private enum ServiceProviderCollection implements ConfigurationServiceProvider.CollectionKey {
		processorIdentifier("training-id", "calamari-training"),
		processorDescription("training-description", "Calamari training processor");

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
		 * @see
		 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.
		 * CollectionKey#getName()
		 */
		@Override
		public String getName() {
			return collectionName;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.
		 * CollectionKey#getKey()
		 */
		@Override
		public String getKey() {
			return key;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.
		 * CollectionKey#getDefaultValue()
		 */
		@Override
		public String getDefaultValue() {
			return defaultValue;
		}
	}

	/**
	 * Default constructor for a service providers for the Calamari training
	 * processor.
	 * 
	 * @since 17
	 */
	public CalamariTraining() {
		super(CalamariTraining.class, Type.training);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#processorIdentifier()
	 */
	@Override
	protected CollectionKey processorIdentifier() {
		return ServiceProviderCollection.processorIdentifier;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#processorDescription()
	 */
	@Override
	protected CollectionKey processorDescription() {
		return ServiceProviderCollection.processorDescription;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getVersion()
	 */
	@Override
	public float getVersion() {
		return 1.0F;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getIndex()
	 */
	@Override
	public int getIndex() {
		return 100;
	}

	/**
	 * Returns the model configuration.
	 * 
	 * @param framework The framework.
	 * @return The model configuration.
	 * @since 17
	 */
	protected ModelConfiguration getModelConfiguration(TrainingFramework framework) {
		return new ModelConfiguration(framework.getModelConfiguration().getFolder(),
				framework.getModelConfiguration().getEngine());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider#
	 * newProcessor()
	 */
	@Override
	public Processor<ProcessorCore.Callback, TrainingFramework> newProcessor() {
		return getProviderDescription() == null ? null
				: new MsaProcessorServiceProvider<ProcessorCore.Callback, TrainingFramework>(
						microserviceArchitecture.getEventController()) {
					/**
					 * The timeout thread.
					 */
					private Thread thread = null;

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

					/**
					 * Maps the msa job state to the execution process state and returns it. The msa
					 * job has to be done.
					 * 
					 * @param state The msa job state.
					 * @return Null if the process executed successfully without any errors.
					 *         Otherwise, the processor trouble execution state.
					 * @since 17
					 */
					private ProcessorCore.State map(
							de.uniwuerzburg.zpd.ocr4all.application.communication.msa.job.State state) {
						switch (state) {
						case canceled:
							return ProcessorCore.State.canceled;
						case completed:
							return null;
						case interrupted:
						default:
							return ProcessorCore.State.interrupted;
						}

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
								+ ") - " + event.getMessage();

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
					public ProcessorCore.State execute(Callback callback, TrainingFramework framework,
							ModelArgument modelArgument) {
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

						TrainingRequest trainingRequest = new TrainingRequest(key,
								getArguments(modelArgument.getArguments()),
								getBatchRecognitionModelArguments(modelArgument.getArguments()), framework.getModelId(),
								getBatch(framework.getDataset()), getModelConfiguration(framework),
								framework.getUser());

						logger.debug(getProcessorIdentifier() + ": process request - key " + key + ", model id '"
								+ trainingRequest.getModelId() + "', arguments '" + trainingRequest.getArguments()
								+ "'.");

						// start the job
						JobResponse jobResponse;
						try {
							jobResponse = restClient.post().uri(executeRequestMapping)
									.contentType(MediaType.APPLICATION_JSON).body(trainingRequest)
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
							logTrouble("could not execute processor, key " + key + " - '" + e.getMessage());

							return ProcessorCore.State.interrupted;
						}

						final int jobId = jobResponse.getId();

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

							return map(systemJobResponse.getState());
						} catch (Exception e) {
							logTrouble("could not restore system information of the job " + jobId + ", key " + key
									+ " - " + e.getMessage());

							return map(jobResponse.getState());
						}
					}
				};
	}

}
