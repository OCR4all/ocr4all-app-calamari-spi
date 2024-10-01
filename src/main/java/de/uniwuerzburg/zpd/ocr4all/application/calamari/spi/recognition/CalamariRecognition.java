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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.RecognitionJobResponse;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.RecognitionRequest;
import de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.CalamariServiceProviderProcessorWorker;
import de.uniwuerzburg.zpd.ocr4all.application.spi.OpticalCharacterRecognitionServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.core.ProcessorCore;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ConfigurationServiceProvider.CollectionKey;
import de.uniwuerzburg.zpd.ocr4all.application.spi.env.ProcessFramework;
import de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.mets.MetsParser;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.mets.MetsResource;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.mets.MetsTag;
import de.uniwuerzburg.zpd.ocr4all.application.spi.util.mets.MetsUtils;

/**
 * Defines service providers for the Calamari recognition processor. The
 * following properties of the service provider collection <b>calamari</b>
 * override the local default settings (<b>key</b>: <i>default value</i>):
 * <ul>
 * <li>recognition-id: recognition</li>
 * <li>recognition-description: Calamari recognition processor</li>
 * <li>recognition-mets-other-role: Calamari recognition mets other role</li>
 * <li>see {@link CalamariServiceProviderProcessorWorker} for remainder
 * settings</li>
 * </ul>
 *
 * @author <a href="mailto:herbert.baier@uni-wuerzburg.de">Herbert Baier</a>
 * @version 1.0
 * @since 17
 */
public class CalamariRecognition extends
		CalamariServiceProviderProcessorWorker<ProcessorCore.LockSnapshotCallback, ProcessFramework, RecognitionRequest, RecognitionJobResponse>
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
		processorDescription("recognition-description", "Calamari recognition (predict) processor"),
		metsOtherRole("recognition-mets-other-role", "layout/segmentation/region");

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
	 * @see de.uniwuerzburg.zpd.ocr4all.application.calamari.spi.core.
	 * CalamariServiceProviderProcessorWorker#getProcessRequest(java.lang.String,
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
	 * CalamariServiceProviderProcessorWorker#preExecuteCallback(de.uniwuerzburg.zpd
	 * .ocr4all. application.spi.env.Framework,
	 * de.uniwuerzburg.zpd.ocr4all.application.spi.model.argument.ModelArgument,
	 * de.uniwuerzburg.zpd.ocr4all.application.calamari.communication.api.
	 * ProcessRequest)
	 */
	@Override
	protected void preExecuteCallback(ProcessFramework framework, ModelArgument modelArgument,
			RecognitionRequest processRequest) throws Exception {
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

		List<PredictFile> predictFiles = new ArrayList<>();
		final Path processorWorkspace = framework.getProcessorWorkspace();

		for (MetsParser.Root.FileGroup.File file : inputFileGroup.getFiles()) {
			if (!file.getId().startsWith(metsFrameworkFileGroup.getInput()))
				throw new IllegalArgumentException("wrong input file id '" + file.getId()
						+ "', since it is not a prefix of file group id '" + metsFrameworkFileGroup.getInput() + "'.");

			final Path inputFile = processorWorkspace.resolve(file.getLocation().getPath());
			if (!Files.exists(inputFile) || Files.isDirectory(inputFile))
				throw new IOException("the required input file '" + inputFile.toString() + "' is not available.");

			PredictFile larexFile = new PredictFile(metsFrameworkFileGroup, file);

			try {
				Files.copy(inputFile, framework.getOutput().resolve(larexFile.getTargetFilename()),
						StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new IOException("cannot copy the required input file '" + inputFile.toString()
						+ "' to output directory - " + e.getMessage() + ".");
			}

			predictFiles.add(larexFile);
		}

		// mets resources
		MetsResource metsResource;
		try {
			metsResource = new MetsResource();
		} catch (Exception e) {
			throw new IOException("internal error: missed mets agent resource - " + e.getMessage() + ".");
		}

		/*
		 * Updates the mets file
		 */
		try {
			// build mets sections
			final StringBuffer metsFileBuffer = new StringBuffer();
			final String sandboxRelativePath = framework.getOutputRelativeProcessorWorkspace().toString();
			final Hashtable<String, String> targetPages = new Hashtable<>();
			for (PredictFile predictFile : predictFiles) {
				targetPages.put(predictFile.getSourceFile().getId(), predictFile.getTargetFileID());

				metsFileBuffer.append(metsResource.getResources(MetsResource.Template.mets_file)
						.replace(MetsResource.Pattern.file_id.getPattern(), predictFile.getTargetFileID())

						.replace(MetsResource.Pattern.file_mime_type.getPattern(),
								predictFile.getSourceFile().getMimeType())

						.replace(MetsResource.Pattern.file_name.getPattern(),
								sandboxRelativePath + "/" + predictFile.getTargetFilename()));
			}

			// update mets file
			final StringBuffer buffer = new StringBuffer();
			MetsTag handlingTag = null;
			final Set<MetsTag> handledMainTags = new HashSet<>();
			final List<String> pageFileIds = new ArrayList<>();
			for (String line : Files.readAllLines(metsPath)) {
				if (handlingTag == null) {
					handlingTag = MetsTag.getMainOpenTag(line);

					if (handlingTag != null && !handledMainTags.add(handlingTag))
						throw new IllegalArgumentException("duplicated main Mets XML tag '" + handlingTag.getTag()
								+ "', file '" + metsPath.toString() + ".");
				} else {
					final boolean isCloseTag = handlingTag.isCloseTag(line);

					switch (handlingTag) {
					case header:
						if (isCloseTag) {
							buffer.append(metsResource.getResources(MetsResource.Template.mets_agent)
									.replace(MetsResource.Pattern.other_role.getPattern(),
											ConfigurationServiceProvider.getValue(configuration,
													ServiceProviderCollection.metsOtherRole))
									.replace(MetsResource.Pattern.software_name.getPattern(),
											getProcessorIdentifier() + " v" + getVersion())
									.replace(MetsResource.Pattern.input_file_group.getPattern(),
											metsFrameworkFileGroup.getInput())
									.replace(MetsResource.Pattern.output_file_group.getPattern(),
											metsFrameworkFileGroup.getOutput())
									.replace(MetsResource.Pattern.parameter.getPattern(),
											objectMapper.writeValueAsString(processRequest)));

							handlingTag = null;
						}

						break;
					case fileSection:
						if (isCloseTag) {
							buffer.append(metsResource.getResources(MetsResource.Template.mets_file_group)
									.replace(MetsResource.Pattern.file_group.getPattern(),
											metsFrameworkFileGroup.getOutput())
									.replace(MetsResource.Pattern.file_template.getPattern(),
											metsFileBuffer.toString()));

							handlingTag = null;
						}

						break;
					case structureMap:
						if (isCloseTag)
							handlingTag = null;
						else if (MetsTag.physicalSequence.isOpenTag(line))
							handlingTag = MetsTag.physicalSequence;

						break;
					case physicalSequence:
						if (isCloseTag)
							handlingTag = null;
						else if (MetsTag.pages.isOpenTag(line))
							handlingTag = MetsTag.pages;

						break;
					case pages:
						if (isCloseTag) {
							for (String fileGroup : pageFileIds)
								buffer.append(metsResource.getResources(MetsResource.Template.mets_page)
										.replace(MetsResource.Pattern.file_id.getPattern(), fileGroup));

							pageFileIds.clear();
							handlingTag = MetsTag.physicalSequence;
						} else if (MetsTag.fileId.isOpenTag(line))
							for (String fileGroup : targetPages.keySet())
								if (line.contains("FILEID=\"" + fileGroup + "\""))
									pageFileIds.add(targetPages.get(fileGroup));

						break;
					case fileId:
					default:
						break;
					}
				}

				buffer.append(line + "\n");
			}

			// persist mets file
			Files.write(metsPath, buffer.toString().getBytes());
		} catch (Exception e) {
			throw new IOException("can not update mets file - " + e.getMessage() + ".");
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
		 * The source mets file.
		 */
		private final MetsParser.Root.FileGroup.File sourceFile;

		/**
		 * The target file id.
		 */
		private final String targetFileID;

		/**
		 * The target file name.
		 */
		private final String targetFilename;

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

			String inputFileGroup = metsFrameworkFileGroup.getInput();
			String outputFileGroup = metsFrameworkFileGroup.getOutput();

			this.sourceFile = sourceFile;

			this.targetFileID = outputFileGroup + sourceFile.getId().substring(inputFileGroup.length());

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
		 * Returns the target file id.
		 *
		 * @return The target file id.
		 * @since 1.8
		 */
		public String getTargetFileID() {
			return targetFileID;
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
