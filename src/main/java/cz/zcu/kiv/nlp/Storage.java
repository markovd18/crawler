package cz.zcu.kiv.nlp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import cz.zcu.kiv.nlp.ir.Utils;

public class Storage {

  private static final Logger log = Logger.getLogger(Storage.class);

  private final String path;

  public Storage(final String path) {
    if (StringUtils.isBlank(path)) {
      throw new IllegalArgumentException("Storage path may not be blank");
    }

    this.path = path;
    createStorageIfNotExists(path);
  }

  public static void createStorageIfNotExists(final String path) {
    var outputDir = new File(path);
    if (outputDir.exists()) {
      log.debug("Output storage already exists. Skipping.");
      return;
    }

    boolean mkdirs = outputDir.mkdirs();
    if (mkdirs) {
      log.info("Output directory created: " + outputDir);
    } else {
      log.error(
          "Output directory can't be created! Please either create it or change the STORAGE parameter.\nOutput directory: "
              + outputDir);
    }
  }

  public Optional<Set<String>> loadUrls(final String urlsPath) {
    File links = new File(path + urlsPath);
    if (links.exists()) {
      return loadUrlsFromStorage(links);
    }

    return Optional.empty();
  }

  public void saveUrls(final Set<String> urls, final String urlsPath) {
    Utils.saveFile(new File(path + urlsPath), urls);
  }

  public File createFile(final String name) {
    return new File(path + "/" + name);
  }

  private Optional<Set<String>> loadUrlsFromStorage(final File storage) {
    try {
      List<String> lines = Utils.readTXTFile(new FileInputStream(storage));
      return Optional.of(lines.stream().collect(Collectors.toSet()));
    } catch (FileNotFoundException e) {
      log.error("Storage with urls was not found", e);
      return Optional.empty();
    }
  }
}
