/**
 * File:     CalamariServiceProviderWorker.java
 * Package:  de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core
 * 
 * Author:   Herbert Baier (herbert.baier@uni-wuerzburg.de)
 * Date:     13.06.2024
 */
package de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core;

import java.security.ProviderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.DescriptionResponse;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProviderCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.MicroserviceArchitecture;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Premise;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Target;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.BooleanField;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.DecimalField;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.Entry;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.IntegerField;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.Model;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.SelectField;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.StringField;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.SystemProcess;

/**
 * Defines Calamari service provider workers.The following properties of the
 * service provider collection <b>calamari</b> override the local default
 * settings (<b>key</b>: <i>default value</i>):
 * <ul>
 * <li>msa-host-id: calamari</li>
 * <li>msa-host-protocol: http</li>
 * <li>msa-timeout-active-processor: 15000</li>
 * </ul>
 * 
 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
 * @version 1.0
 * @since 17
 */
public abstract class CalamariServiceProviderWorker extends ServiceProviderCore {
	/**
	 * The collection name.
	 */
	protected static final String collectionName = "calamari";

	/**
	 * Defines service provider collection with keys and default values. Collection
	 * blank values are not allowed and their values are trimmed.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	private enum ServiceProviderCollection implements ConfigurationServiceProvider.CollectionKey {
		hostId("msa-host-id", "calamari"), applicationLayerProtocol("msa-host-protocol", "http"),
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
	 * The api context path.
	 */
	public static final String apiContextPath = "/api";

	/**
	 * The api version 1.0 prefix path.
	 */
	public static final String apiContextPathVersion_1_0 = apiContextPath + "/v1.0/";

	/**
	 * The scheduler controller context path.
	 */
	private static final String schedulerControllerContextPath = apiContextPathVersion_1_0 + "scheduler/";

	/**
	 * The ping request mapping.
	 */
	public static final String pingRequestMapping = schedulerControllerContextPath + "ping";

	/**
	 * The job request mapping.
	 */
	public static final String jobRequestMapping = schedulerControllerContextPath + "job/{id}";

	/**
	 * The expunge job request mapping.
	 */
	public static final String expungeJobRequestMapping = schedulerControllerContextPath + "expunge/{id}";

	/**
	 * Defines types.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 17
	 */
	protected enum Type {
		evaluation, recognition, training
	}

	/**
	 * The JSON object mapper.
	 */
	protected final ObjectMapper objectMapper = new ObjectMapper();
	{
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	/**
	 * The logger.
	 */
	protected final org.slf4j.Logger logger;

	/**
	 * The type.
	 */
	private final Type type;

	/**
	 * The processor description request mapping.
	 */
	private final String descriptionRequestMapping;

	/**
	 * The processor json execute request mapping.
	 */
	protected final String executeRequestMapping;

	/**
	 * The system job request mapping.
	 */
	protected final String systemJobRequestMapping;

	/**
	 * The provider description.
	 */
	private DescriptionResponse providerDescription = null;

	/**
	 * The client to perform HTTP requests.
	 */
	protected RestClient restClient = null;

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
	public CalamariServiceProviderWorker(Class<?> logger, Type type) {
		super();

		this.logger = org.slf4j.LoggerFactory.getLogger(logger);
		this.type = type;

		descriptionRequestMapping = apiContextPathVersion_1_0 + type.name() + "/description";
		executeRequestMapping = apiContextPathVersion_1_0 + type.name() + "/execute";
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getProvider(
	 * )
	 */
	@Override
	public String getProvider() {
		return "calamari/" + type.name();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getName(java
	 * .util.Locale)
	 */
	@Override
	public String getName(Locale locale) {
		return getProcessorIdentifier();
	}

	/**
	 * Returns the service provider collection with key and default value for
	 * processor name.
	 * 
	 * @return The service provider collection with key and default value for
	 *         processor name.
	 * @since 1.8
	 */
	protected abstract ConfigurationServiceProvider.CollectionKey processorIdentifier();

	/**
	 * Returns the processor identifier.
	 * 
	 * @return The processor identifier.
	 * @since 1.8
	 */
	protected String getProcessorIdentifier() {
		return ConfigurationServiceProvider.getValue(configuration, processorIdentifier());
	}

	/**
	 * Returns the service provider collection with key and default value for
	 * processor description.
	 * 
	 * @return The service provider collection with key and default value for
	 *         processor description.
	 * @since 1.8
	 */
	protected abstract ConfigurationServiceProvider.CollectionKey processorDescription();

	/**
	 * Returns the processor description.
	 * 
	 * @return The processor description.
	 * @since 1.8
	 */
	protected String getProcessorDescription() {
		return ConfigurationServiceProvider.getValue(configuration, processorDescription());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProviderCore#
	 * initializeCallback()
	 */
	@Override
	protected void initializeCallback() throws ProviderException {
		final String hostId = configuration.getValue(ServiceProviderCollection.hostId);

		MicroserviceArchitecture.Host host = microserviceArchitecture.getHost(hostId);

		if (host == null)
			throw new ProviderException("unknown host configuration for msa id " + hostId + ".");

		final String url = configuration.getValue(ServiceProviderCollection.applicationLayerProtocol) + "://"
				+ host.getUrl();

		restClient = RestClient.create(url);

		try {
			providerDescription = restClient.get().uri(descriptionRequestMapping).accept(MediaType.APPLICATION_JSON)
					.retrieve().onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
						throw new ProviderException("HTTP client error status " + response.getStatusCode() + " ("
								+ response.getStatusText() + "): " + response.getHeaders());
					}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
						throw new ProviderException("HTTP server error status " + response.getStatusCode() + " ("
								+ response.getStatusText() + "): " + response.getHeaders());
					}).body(DescriptionResponse.class);
		} catch (Exception e) {
			logger.warn(type.name() + "provider could not be initialized (" + url + "/" + descriptionRequestMapping
					+ ") - " + e.getMessage());

			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProviderCore#
	 * startCallback()
	 */
	@Override
	protected void startCallback() throws ProviderException {
		if (providerDescription == null)
			initializeCallback();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProviderCore#
	 * restartCallback()
	 */
	@Override
	public void restartCallback() throws ProviderException {
		startCallback();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#
	 * getDescription(java.util.Locale)
	 */
	@Override
	public Optional<String> getDescription(Locale locale) {
		return providerDescription == null ? super.getDescription(locale)
				: Optional.of(providerDescription.getDescription());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#
	 * getCategories()
	 */
	@Override
	public List<String> getCategories() {
		return providerDescription == null ? null : providerDescription.getCategories();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getSteps()
	 */
	@Override
	public List<String> getSteps() {
		return providerDescription == null ? null : providerDescription.getSteps();
	}

	/**
	 * Ping the client to check the status of an HTTP request.
	 * 
	 * @throws ProviderException Throws on HTTP request troubles.
	 * @since 17
	 */
	private void ping() throws ProviderException {
		restClient.get().uri(pingRequestMapping).retrieve()
				.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
					throw new ProviderException("HTTP client error status " + response.getStatusCode() + " ("
							+ response.getStatusText() + "): " + response.getHeaders());
				}).onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
					throw new ProviderException("HTTP server error status " + response.getStatusCode() + " ("
							+ response.getStatusText() + "): " + response.getHeaders());
				}).toBodilessEntity();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getPremise(
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.Target)
	 */
	@Override
	public Premise getPremise(Target target) {
		try {
			ping();

			return new Premise();
		} catch (ProviderException e) {
			final String message = "trouble contacting ocrd msa - " + e.getMessage();

			logger.warn(type.name() + ": " + message);

			return new Premise(Premise.State.block, locale -> message);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProvider#getModel(de.
	 * uniwuerzburg.zpd.ocr4all.application.spi.env.Target)
	 */
	@Override
	public Model getModel(Target target) {
		if (providerDescription == null)
			return null;
		else {
			List<SortEntry> entries = new ArrayList<>();

			if (providerDescription.getModel().getBooleans() != null)
				for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.BooleanField entry : providerDescription
						.getModel().getBooleans())
					entries.add(new SortEntry(entry.getIndex(),
							new BooleanField(entry.getArgument(), entry.getDefaultValue(), (locale) -> entry.getLabel(),
									(locale) -> entry.getDescription(), entry.isDisabled())));

			if (providerDescription.getModel().getDecimals() != null)
				for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.DecimalField entry : providerDescription
						.getModel().getDecimals())
					entries.add(new SortEntry(entry.getIndex(),
							new DecimalField(entry.getArgument(), entry.getDefaultValue(), (locale) -> entry.getLabel(),
									(locale) -> entry.getDescription(), (locale) -> entry.getPlaceholder(),
									entry.getStep(), entry.getMinimum(), entry.getMaximum(), null,
									entry.isDisabled())));

			if (providerDescription.getModel().getIntegers() != null)
				for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.IntegerField entry : providerDescription
						.getModel().getIntegers())
					entries.add(new SortEntry(entry.getIndex(),
							new IntegerField(entry.getArgument(), entry.getDefaultValue(), (locale) -> entry.getLabel(),
									(locale) -> entry.getDescription(), (locale) -> entry.getPlaceholder(),
									entry.getStep(), entry.getMinimum(), entry.getMaximum(), null,
									entry.isDisabled())));

			if (providerDescription.getModel().getStrings() != null)
				for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.StringField entry : providerDescription
						.getModel().getStrings())
					entries.add(new SortEntry(entry.getIndex(),
							new StringField(entry.getArgument(), entry.getDefaultValue(), (locale) -> entry.getLabel(),
									(locale) -> entry.getDescription(), (locale) -> entry.getPlaceholder(),
									entry.isDisabled())));

			if (providerDescription.getModel().getSelects() != null)
				for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.SelectField entry : providerDescription
						.getModel().getSelects()) {

					List<SelectField.Item> items = new ArrayList<>();
					for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.SelectField.Item item : entry
							.getItems())
						items.add(new SelectField.Option(item.isSelected(), item.getValue(),
								(locale) -> item.getDescription(), item.isDisabled()));

					entries.add(new SortEntry(entry.getIndex(),
							new SelectField(entry.getArgument(), (locale) -> entry.getLabel(),
									(locale) -> entry.getDescription(), entry.isMultipleOptions(), items,
									entry.isDisabled())));
				}

			return new Model(SortEntry.getSorted(entries));
		}
	}

	/**
	 * Defines sort entries.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @param <T>
	 * @since 17
	 */
	private static class SortEntry {
		/**
		 * The index.
		 */
		private final int index;

		/**
		 * The entry.
		 */
		private final Entry entry;

		/**
		 * Creates a sort entry.
		 * 
		 * @param index The index.
		 * @param entry The entry.
		 * @since 17
		 */
		public SortEntry(int index, Entry entry) {
			super();
			this.index = index;
			this.entry = entry;
		}

		/**
		 * Returns the index.
		 *
		 * @return The index.
		 * @since 17
		 */
		public int getIndex() {
			return index;
		}

		/**
		 * Returns the entry.
		 *
		 * @return The entry.
		 * @since 17
		 */
		public Entry getEntry() {
			return entry;
		}

		/**
		 * Returns the sorted model entries.
		 * 
		 * @param entries The entries to sort.
		 * @return The sorted model entries.
		 * @since 17
		 */
		public static List<Entry> getSorted(List<SortEntry> entries) {
			Collections.sort(entries, new Comparator<SortEntry>() {

				/*
				 * (non-Javadoc)
				 * 
				 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
				 */
				@Override
				public int compare(SortEntry o1, SortEntry o2) {
					return o1.getIndex() - o2.getIndex();
				}
			});

			List<Entry> list = new ArrayList<>();
			for (SortEntry entry : entries)
				list.add(entry.getEntry());

			return list;
		}
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
