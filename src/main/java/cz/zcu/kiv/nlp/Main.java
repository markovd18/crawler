package cz.zcu.kiv.nlp;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import cz.zcu.kiv.nlp.ir.HTMLDownloaderSelenium;
import cz.zcu.kiv.nlp.vs.Crawler;

public class Main {

  private static final String STORAGE = "./storage/hokej-cz";
  private static final int POLITENESS_INTERVAL_MILLIS = 1200;

  public static void main(final String[] args) {
    initialize();
    final var storage = new Storage(STORAGE);

    var crawler = createCrawler(storage);
    crawler.crawl();
  }

  private static void initialize() {
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.INFO);
  }

  private static Crawler createCrawler(final Storage storage) {
    final var downloader = new HTMLDownloaderSelenium();
    return new Crawler(downloader, POLITENESS_INTERVAL_MILLIS, storage);
  }
}
