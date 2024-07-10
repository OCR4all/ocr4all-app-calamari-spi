/**
 * File:     CalamariTraining.java
 * Package:  de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.training
 * 
 * Author:   Herbert Baier (herbert.baier@uni-wuerzburg.de)
 * Date:     26.06.2024
 */
package de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.training;

import java.nio.file.Path;
import java.nio.file.Paths;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.TrainingJobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.TrainingRequest;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.core.ModelConfiguration;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.CalamariServiceProviderWorker;
import de.uniwuerzburg.zpd.ocr4all.application.communication.msa.api.domain.JobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.persistence.PersistenceManager;
import de.uniwuerzburg.zpd.ocr4all.application.persistence.assemble.Engine;
import de.uniwuerzburg.zpd.ocr4all.application.spi.TrainingServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorCore.State;
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
public class CalamariTraining extends
		CalamariServiceProviderWorker<ProcessorCore.Callback, TrainingFramework, TrainingRequest, TrainingJobResponse>
		implements TrainingServiceProvider {

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
		super(CalamariTraining.class, Type.training, TrainingJobResponse.class);
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
	 * Returns the engine configuration.
	 * 
	 * @param framework The framework for the processor.
	 * @return The engine configuration.
	 * @since 17
	 */
	private Path getEngineConfiguration(TrainingFramework framework) {
		return Paths.get(framework.getAssemble().toString(), framework.getModelId(),
				framework.getModelConfiguration().getFolder(), framework.getModelConfiguration().getEngine());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#getProcessRequest(java.lang.String,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
	 */
	@Override
	protected TrainingRequest getProcessRequest(String key, TrainingFramework framework, ModelArgument modelArgument) {
		final ModelConfiguration modelConfiguration = new ModelConfiguration(
				framework.getModelConfiguration().getFolder());

		return new TrainingRequest(key, getArguments(modelArgument.getArguments()),
				getBatchRecognitionModelArguments(modelArgument.getArguments()), framework.getModelId(),
				getBatch(framework.getDataset()), modelConfiguration);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#posExecuteCallback(de.uniwuerzburg.zpd.ocr4all.
	 * application.communication.msa.api.domain.JobResponse,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
	 */
	@Override
	protected JobResponse posExecuteCallback(TrainingJobResponse job, TrainingFramework framework,
			ModelArgument modelArgument) throws Exception {

		Engine engine = job.getEngine();
		engine.setUser(framework.getUser());

		(new PersistenceManager(getEngineConfiguration(framework),
				de.uniwuerzburg.zpd.ocr4all.application.persistence.Type.assemble_engine_v1)).persist(engine);

		return job;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#postProcessingCallback(de.uniwuerzburg.zpd.
	 * ocr4all.application.spi.core.ProcessorCore.State,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
	 */
	@Override
	protected State postProcessingCallback(State state, TrainingFramework framework, ModelArgument modelArgument)
			throws Exception {
		Path folder = getEngineConfiguration(framework);

		PersistenceManager persistenceManager = new PersistenceManager(folder,
				de.uniwuerzburg.zpd.ocr4all.application.persistence.Type.assemble_engine_v1);

		Engine engine = persistenceManager.getEntity(Engine.class);

		if (engine == null)
			throw new IllegalStateException("the required engine file '" + folder.toString() + "' is not available");

		Engine.State engineState;
		switch (state) {
		case canceled:
			engineState = Engine.State.canceled;
			break;
		case completed:
			engineState = Engine.State.completed;
			break;
		case interrupted:
		default:
			engineState = Engine.State.interrupted;
		}

		engine.setState(engineState);

		persistenceManager.persist(engine);

		return state;
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
		return newCalamaryProcessor();
	}

}
