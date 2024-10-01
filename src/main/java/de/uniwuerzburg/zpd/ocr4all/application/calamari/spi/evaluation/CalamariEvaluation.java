/**
 * File:     CalamariEvaluation.java
 * Package:  de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.recognition
 * 
 * Author:   Herbert Baier (herbert.baier@uni-wuerzburg.de)
 * Date:     11.07.2024
 */
package de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.evaluation;

import java.security.ProviderException;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.EvaluationRequest;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.CalamariServiceProviderProcessorWorker;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.CalamariServiceProviderWorker;
import de.uniwuerzburg.zpd.ocr4all.application.communication.action.EvaluationMeasure;
import de.uniwuerzburg.zpd.ocr4all.application.communication.spi.ServiceProviderTask;
import de.uniwuerzburg.zpd.ocr4all.application.spi.ActionServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.CollectionKey;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Database;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument;

/**
 * Defines service providers for the Calamari evaluation processor. The
 * following properties of the service provider collection <b>calamari</b>
 * override the local default settings (<b>key</b>: <i>default value</i>):
 * <ul>
 * <li>evaluation-id: evaluation</li>
 * <li>evaluation-description: Calamari evaluation processor</li>
 * <li>see {@link CalamariServiceProviderProcessorWorker} for remainder
 * settings</li>
 * </ul>
 *
 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
 * @version 1.0
 * @since 17
 */
public class CalamariEvaluation extends CalamariServiceProviderWorker implements ActionServiceProvider {

	/**
	 * Defines service provider collection with keys and default values. Collection
	 * blank values are not allowed and their values are trimmed.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	private enum ServiceProviderCollection implements ConfigurationServiceProvider.CollectionKey {
		processorIdentifier("evaluation-id", "calamari-eval"),
		processorDescription("evaluation-description", "Calamari eval (evaluation) processor");

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
	 * Default constructor for a service providers for the Calamari evaluation
	 * processor.
	 * 
	 * @since 17
	 */
	public CalamariEvaluation() {
		super(CalamariEvaluation.class, Type.evaluation);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderProcessorWorker#processorIdentifier()
	 */
	@Override
	protected CollectionKey processorIdentifier() {
		return ServiceProviderCollection.processorIdentifier;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderProcessorWorker#processorDescription()
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.core.WorkerServiceProvider#
	 * newAgent()
	 */
	@Override
	public Worker<Database> newAgent() {
		return providerDescription == null ? null : new Worker<Database>() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.WorkerServiceProvider.Worker
			 * #execute(de.uniwuerzburg.zpd.ocr4all.application.spi.env.Database,
			 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
			 */
			@Override
			public ServiceProviderTask execute(Database database, ModelArgument modelArgument) {
				EvaluationRequest evaluationRequest = new EvaluationRequest(database.getName(),
						getArguments(modelArgument.getArguments()));

				EvaluationMeasure evaluation;
				try {
					evaluation = restClient.post().uri(executeRequestMapping).contentType(MediaType.APPLICATION_JSON)
							.body(evaluationRequest).accept(MediaType.APPLICATION_JSON).retrieve()
							.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
								throw new ProviderException("HTTP client error status " + response.getStatusCode()
										+ " (" + response.getStatusText() + "): " + response.getHeaders());
							}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
								throw new ProviderException("HTTP server error status " + response.getStatusCode()
										+ " (" + response.getStatusText() + "): " + response.getHeaders());
							}).body(EvaluationMeasure.class);
				} catch (Exception e) {
					evaluation = new EvaluationMeasure(EvaluationMeasure.State.interrupted, e);
					
					logger.warn(getProcessorIdentifier() + ": " + "could not execute processor - "
							+ e.getMessage());
				}

				return evaluation;
			}
		};
	}

}
