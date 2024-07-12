/**
 * File:     CalamariRecognition.java
 * Package:  de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.recognition
 * 
 * Author:   Herbert Baier (herbert.baier@uni-wuerzburg.de)
 * Date:     11.07.2024
 */
package de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.recognition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.RecognitionJobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.RecognitionRequest;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.CalamariServiceProviderWorker;
import de.uniwuerzburg.zpd.ocr4all.application.spi.OpticalCharacterRecognitionServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.CollectionKey;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ProcessFramework;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.mets.MetsParser;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.mets.MetsUtils;

/**
 * Defines service providers for the Calamari recognition processor. The
 * following properties of the service provider collection <b>calamari</b>
 * override the local default settings (<b>key</b>: <i>default value</i>):
 * <ul>
 * <li>recognition-id: recognition</li>
 * <li>recognition-description: Calamari recognition processor</li>
 * <li>see {@link CalamariServiceProviderWorker} for remainder settings</li>
 * </ul>
 *
 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
 * @version 1.0
 * @since 17
 */
public class CalamariRecognition extends
		CalamariServiceProviderWorker<ProcessorCore.LockSnapshotCallback, ProcessFramework, RecognitionRequest, RecognitionJobResponse>
		implements OpticalCharacterRecognitionServiceProvider {

	/**
	 * Defines service provider collection with keys and default values. Collection
	 * blank values are not allowed and their values are trimmed.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	private enum ServiceProviderCollection implements ConfigurationServiceProvider.CollectionKey {
		processorIdentifier("recognition-id", "calamari-predict"),
		processorDescription("recognition-description", "Calamari recognition (predict) processor");

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
	 * Default constructor for a service providers for the Calamari recognition
	 * processor.
	 * 
	 * @since 17
	 */
	public CalamariRecognition() {
		super(CalamariRecognition.class, Type.recognition, RecognitionJobResponse.class);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#getProcessRequest(java.lang.String,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.env.Framework,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
	 */
	@Override
	protected RecognitionRequest getProcessRequest(String key, ProcessFramework framework,
			ModelArgument modelArgument) {
		return new RecognitionRequest(key, getArguments(modelArgument.getArguments()),
				getBatchRecognitionModelArguments(modelArgument.getArguments()),
				framework.getOutputRelativeProjects().toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderWorker#preExecuteCallback(de.uniwuerzburg.zpd.ocr4all.
	 * application.spi.env.Framework,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument)
	 */
	@Override
	protected void preExecuteCallback(ProcessFramework framework, ModelArgument modelArgument) throws Exception {
		// mets file
		final Path metsPath = framework.getMets();
		if (metsPath == null)
			throw new IOException("missed required mets file path.");

		final String metsGroup = framework.getMetsGroup();
		if (metsGroup == null)
			throw new IllegalArgumentException("missed required mets file group.");

		final MetsUtils.FrameworkFileGroup metsFrameworkFileGroup = MetsUtils.getFileGroup(framework);
		final MetsParser.Root root;

		// parse mets file
		if (Files.exists(metsPath))
			try {
				root = (new MetsParser()).deserialise(metsPath.toFile());
			} catch (Exception e) {
				throw new IOException("could not parse the mets file - " + e.getMessage());
			}
		else
			throw new IOException("the mets file is not available.");

		/*
		 * Select required files and copy then to temporary directory
		 */

		// search for input file group
		MetsParser.Root.FileGroup inputFileGroup = null;
		for (MetsParser.Root.FileGroup fileGroup : root.getFileGroups())
			if (metsFrameworkFileGroup.getInput().equals(fileGroup.getId())) {
				inputFileGroup = fileGroup;

				break;
			}

		if (inputFileGroup == null)
			throw new IllegalArgumentException(
					"the required mets input file group '" + metsFrameworkFileGroup.getInput() + "' is not available.");

		List<PredictFile> larexFiles = new ArrayList<>();
		final Path processorWorkspace = framework.getProcessorWorkspace();

		for (MetsParser.Root.FileGroup.File file : inputFileGroup.getFiles()) {
			if (!file.getId().startsWith(metsFrameworkFileGroup.getInput()))
				throw new IllegalArgumentException("Wrong input file id '" + file.getId()
						+ "', since it is not a prefix of file group id '" + metsFrameworkFileGroup.getInput() + "'.");

			final Path inputFile = Paths.get(processorWorkspace.toString(), file.getLocation().getPath());
			if (!Files.exists(inputFile) || Files.isDirectory(inputFile))
				throw new IOException("The required input file '" + inputFile.toString() + "' is not available.");

			PredictFile larexFile = new PredictFile(metsFrameworkFileGroup, file);

			try {
				Files.copy(inputFile, framework.getOutput().resolve(larexFile.getXmlContainer().getTargetFilename()),
						StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new IOException("cannot copy the required input file '" + inputFile.toString()
						+ "' to output directory - " + e.getMessage() + ".");
			}

			larexFiles.add(larexFile);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorServiceProvider#
	 * newProcessor()
	 */
	@Override
	public Processor<ProcessorCore.LockSnapshotCallback, ProcessFramework> newProcessor() {
		return newCalamaryProcessor();
	}

	/**
	 * PredictFile is an immutable class that defines predict files.
	 *
	 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
	 * @version 1.0
	 * @since 1.8
	 */
	private static class PredictFile {
		/**
		 * The container type.
		 */
		private enum ContainerType {
			xml, image
		}

		/**
		 * The next file index.
		 */
		private static int nextFileIndex = 0;

		/**
		 * The mets framework file group.
		 */
		private final MetsUtils.FrameworkFileGroup metsFrameworkFileGroup;

		/**
		 * The xml container.
		 */
		private final Container xmlContainer;

		/**
		 * The image container.
		 */
		private Container imageContainer = null;

		/**
		 * Creates a predict file.
		 * 
		 * @param metsFrameworkFileGroup The mets framework file group.
		 * @param sourceFile             The source mets file.
		 * @since 1.8
		 */
		public PredictFile(MetsUtils.FrameworkFileGroup metsFrameworkFileGroup,
				MetsParser.Root.FileGroup.File sourceFile) {
			super();
			this.metsFrameworkFileGroup = metsFrameworkFileGroup;

			xmlContainer = new Container(ContainerType.xml, sourceFile, null, this.metsFrameworkFileGroup.getInput(),
					this.metsFrameworkFileGroup.getOutput());
		}

		/**
		 * Returns the xml container.
		 *
		 * @return The xml container.
		 * @since 1.8
		 */
		public Container getXmlContainer() {
			return xmlContainer;
		}

		/**
		 * Returns true if the image container is set.
		 *
		 * @return True if the image container is set.
		 * @since 1.8
		 */
		public boolean isImageContainerSet() {
			return imageContainer != null;
		}

		/**
		 * Returns the image container.
		 *
		 * @return The image container.
		 * @since 1.8
		 */
		public Container getImageContainer() {
			return imageContainer;
		}

		/**
		 * Set the image container.
		 *
		 * @param imageContainer The image container to set.
		 * @since 1.8
		 */
		public void setImageContainer(List<Integer> track, MetsParser.Root.FileGroup.File sourceFile) {
			imageContainer = new Container(ContainerType.image, sourceFile, xmlContainer.getTargetFileCoreID(),
					metsFrameworkFileGroup.getFileGroup(track), metsFrameworkFileGroup.getOutput());
		}

		/**
		 * Container is an immutable class that defines containers.
		 *
		 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
		 * @version 1.0
		 * @since 1.8
		 */
		public class Container {
			/**
			 * The file index.
			 */
			private final int fileIndex;

			/**
			 * The container type.
			 */
			private final ContainerType type;

			/**
			 * The source mets file.
			 */
			private final MetsParser.Root.FileGroup.File sourceFile;

			/**
			 * The target file core id.
			 */
			private final String targetFileCoreID;

			/**
			 * The target file name.
			 */
			private final String targetFilename;

			/**
			 * Creates a container.
			 * 
			 * @param type             The container type.
			 * @param sourceFile       The source mets file.
			 * @param targetFileCoreID The source mets file.
			 * @param targetFileCoreID The target file id.
			 * @param targetFilename   The target file name.
			 * @since 1.8
			 */
			private Container(ContainerType type, MetsParser.Root.FileGroup.File sourceFile, String targetFileCoreID,
					String inputFileGroup, String outputFileGroup) {
				super();

				this.fileIndex = ++nextFileIndex;
				this.type = type;
				this.sourceFile = sourceFile;

				this.targetFileCoreID = targetFileCoreID != null ? targetFileCoreID
						: outputFileGroup + (sourceFile.getId().startsWith(inputFileGroup)
								? sourceFile.getId().substring(inputFileGroup.length())
								: "_" + fileIndex);

				final String sourceFilename = Paths.get(sourceFile.getLocation().getPath()).getFileName().toString();
				targetFilename = sourceFilename.startsWith(inputFileGroup)
						? outputFileGroup + sourceFilename.substring(inputFileGroup.length())
						: sourceFilename;
			}

			/**
			 * Returns the source mets file.
			 *
			 * @return The source mets file.
			 * @since 1.8
			 */
			public MetsParser.Root.FileGroup.File getSourceFile() {
				return sourceFile;
			}

			/**
			 * Returns the target file core id.
			 *
			 * @return The target file core id.
			 * @since 1.8
			 */
			public String getTargetFileCoreID() {
				return targetFileCoreID;
			}

			/**
			 * Returns the target file id.
			 *
			 * @return The target file id.
			 * @since 1.8
			 */
			public String getTargetFileID() {
				return targetFileCoreID + "_" + type.name();
			}

			/**
			 * Returns the target file name.
			 * 
			 * @return The target file name.
			 * @since 1.8
			 */
			public String getTargetFilename() {
				return targetFilename;
			}
		}
	}
}
