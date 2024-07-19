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
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ServiceProviderCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Dataset;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework;
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
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.WeightField;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.Argument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.BooleanArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.DecimalArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.IntegerArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.SelectArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.StringArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.WeightArgument;
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
public abstract class CalamariServiceProviderWorker<C extends ProcessorCore.Callback, F extends Framework, P extends ProcessRequest, J extends JobResponse>
		extends ServiceProviderCore {
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
	 * The cancel job request mapping.
	 */
	public static final String cancelJobRequestMapping = "/cancel/{id}";

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
	 * The job type.
	 */
	private final Class<J> jobType;;

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
	public CalamariServiceProviderWorker(Class<?> logger, Type type, Class<J> jobType) {
		super();

		this.logger = org.slf4j.LoggerFactory.getLogger(logger);
		this.type = type;
		this.jobType = jobType;

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
	protected void ping() throws ProviderException {
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

			if (providerDescription.getModel().getWeights() != null)
				for (de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.model.WeightField entry : providerDescription
						.getModel().getWeights())
					entries.add(new SortEntry(entry.getIndex(),
							new WeightField(entry.getArgument(), (locale) -> entry.getLabel(),
									(locale) -> entry.getDescription(), (locale) -> entry.getPlaceholder(),
									WeightField.Type.Calamari, entry.getMinimumVersion(), entry.getMaximumVersion(),
									entry.isMultipleSelect(), entry.getSuffix(), entry.isDisabled())));

			return new Model(SortEntry.getSorted(entries));
		}
	}

	/**
	 * Returns the arguments select, string, integer, decimal and boolean with their
	 * values for a system job process.
	 *
	 * @param arguments The arguments for the spi model.
	 * @return The arguments select, string, integer, decimal and boolean with their
	 *         values for a system job process.
	 * @since 17
	 */
	protected List<String> getArguments(List<Argument> arguments) throws IllegalArgumentException {
		List<String> list = new ArrayList<>();

		if (arguments != null)
			for (Argument argument : arguments)
				if (argument instanceof SelectArgument select) {
					if (select.getValues().isPresent()) {
						List<String> values = select.getValues().get();

						if (values.size() >= 1) {
							list.add(select.getArgument());

							for (String value : values)
								list.add(value);
						}
					}
				} else if (argument instanceof StringArgument string) {
					if (string.getValue().isPresent()) {
						list.add(string.getArgument());
						list.add(string.getValue().get());
					}

				} else if (argument instanceof IntegerArgument integer) {
					if (integer.getValue().isPresent()) {
						list.add(integer.getArgument());
						list.add("" + integer.getValue().get());

					}
				} else if (argument instanceof DecimalArgument decimal) {
					if (decimal.getValue().isPresent()) {
						list.add(decimal.getArgument());
						list.add("" + (float) decimal.getValue().get());
					}
				} else if (argument instanceof BooleanArgument bool) {
					if (bool.getValue().isPresent() && bool.getValue().get())
						list.add(bool.getArgument());
				}

		return list;
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
							logTrouble("could not execute processor, key " + key + " - '" + e.getMessage());

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
