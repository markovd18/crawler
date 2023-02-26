package cz.zcu.kiv.nlp.vs;

import cz.zcu.kiv.nlp.Storage;
import cz.zcu.kiv.nlp.ir.HTMLDownloaderInterface;
import cz.zcu.kiv.nlp.ir.Utils;
import cz.zcu.kiv.nlp.utils.Links;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CrawlerVSCOM class acts as a controller. You should only adapt this file to
 * serve your needs.
 * Created by Tigi on 31.10.2014.
 */
public class Crawler {
    /**
     * Xpath expressions to extract and their descriptions.
     */
    private final static Map<String, String> xpathMap = new HashMap<String, String>();

    static {
        xpathMap.put("allText", "//div[contains(@class, 'article')]/allText()");
        xpathMap.put("html", "//div[contains(@class, 'article')]/html()");
        xpathMap.put("tidyText", "//div[contains(@class, 'article')]/tidyText()");
    }

    private static String SITE = "https://www.hokej.cz";

    private static String URLS_STORAGE_PATH = "_urls.txt";

    private static final Logger log = Logger.getLogger(Crawler.class);

    /**
     * Be polite and don't send requests too often.
     * Waiting period between requests.
     */
    private final int politenessIntervalMillis;
    private final HTMLDownloaderInterface downloader;
    private final Storage storage;

    public Crawler(final HTMLDownloaderInterface downloader, final int politenessIntervalMillis,
            final Storage storage) {
        validateParams(downloader, politenessIntervalMillis, storage);

        this.downloader = downloader;
        this.politenessIntervalMillis = politenessIntervalMillis;
        this.storage = storage;
    }

    private void validateParams(final HTMLDownloaderInterface downloader, final int politenessIntervalMillis,
            final Storage storage) {
        if (downloader == null) {
            throw new IllegalArgumentException("Downloader may not be null");
        }

        if (politenessIntervalMillis <= 0) {
            throw new IllegalArgumentException("Politeness interval has to be a positive integer");
        }

        if (storage == null) {
            throw new IllegalArgumentException("Storage may not be null");
        }
    }

    public void crawl() {
        Map<String, Map<String, List<String>>> results = new HashMap<String, Map<String, List<String>>>();

        for (String key : xpathMap.keySet()) {
            Map<String, List<String>> map = new HashMap<String, List<String>>();
            results.put(key, map);
        }

        final var urlsResult = loadUrls();
        if (urlsResult.isEmpty()) {
            log.error("Error while loading urls");
            return;
        }

        final var urls = urlsResult.get();
        storage.saveUrls(urls, URLS_STORAGE_PATH);

        final var printStreamMap = initiatePrintStreams(results);

        int count = 0;
        for (String url : urls) {
            processUrl(url, count, urls.size(), results, printStreamMap);
            count++;

            waitForPolitenessDuration();
        }

        closePrintStreams(results, printStreamMap);

        // Save links that failed in some way.
        // Be sure to go through these and explain why the process failed on these
        // links.
        // Try to eliminate all failed links - they consume your time while crawling
        // data.
        reportProblems(downloader.getFailedLinks());
        downloader.emptyFailedLinks();
        log.info("-----------------------------");
    }

    private Optional<Set<String>> loadUrls() {
        final var storedUrls = storage.loadUrls(URLS_STORAGE_PATH);
        if (storedUrls.isPresent()) {
            return storedUrls;
        }

        return Optional.of(crawlUrlsFromWebsite());
    }

    private Set<String> crawlUrlsFromWebsite() {
        final var mainArticleUrls = downloader.getLinks(SITE,
                "//section[@class='h-posts-section']//div[@class='h-posts-box']//article/a[starts-with(@href, '/')]/@href")
                .stream()
                .collect(Collectors.toSet());

        final var sideListArticleUrls = downloader.getLinks(SITE,
                "//section[@class='h-posts-section']//ul[@class='h-posts-list']//li/h3/a[starts-with(@href, '/')]/@href")
                .stream()
                .collect(Collectors.toSet());

        mainArticleUrls.addAll(sideListArticleUrls);
        return mainArticleUrls;
    }

    private Map<String, PrintStream> initiatePrintStreams(final Map<String, Map<String, List<String>>> results) {
        Map<String, PrintStream> printStreamMap = new HashMap<String, PrintStream>();
        for (String key : results.keySet()) {
            File file = storage.createFile(Utils.SDF.format(System.currentTimeMillis()) + "_" + key + ".txt");
            PrintStream printStream = null;
            try {
                printStream = new PrintStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            printStreamMap.put(key, printStream);
        }

        return printStreamMap;
    }

    private void closePrintStreams(final Map<String, Map<String, List<String>>> results,
            final Map<String, PrintStream> printStreamMap) {
        for (String key : results.keySet()) {
            PrintStream printStream = printStreamMap.get(key);
            printStream.close();
        }
    }

    private void processUrl(final String url, final int order, final int totalCount,
            final Map<String, Map<String, List<String>>> results,
            final Map<String, PrintStream> printStreamMap) {
        final var link = Links.prependBaseUrlIfNeeded(url, SITE);
        // Download and extract data according to xpathMap
        Map<String, List<String>> products = downloader.processUrl(link, xpathMap);
        if (order % 100 == 0) {
            log.info(order + " / " + totalCount + " = " + order / ((float) totalCount) + "% done.");
        }
        for (String key : results.keySet()) {
            Map<String, List<String>> map = results.get(key);
            List<String> list = products.get(key);
            if (list == null) {
                continue;
            }

            map.put(url, list);
            log.info(Arrays.toString(list.toArray()));
            // print
            PrintStream printStream = printStreamMap.get(key);
            for (String result : list) {
                printStream.println(url + "\t" + result);
            }
        }
    }

    private void waitForPolitenessDuration() {
        try {
            Thread.sleep(politenessIntervalMillis);
        } catch (InterruptedException e) {
            log.error("Error while performing sleep", e);
        }
    }

    /**
     * Save file with failed links for later examination.
     *
     * @param failedLinks links that couldn't be downloaded, extracted etc.
     */
    private void reportProblems(Set<String> failedLinks) {
        if (failedLinks.isEmpty()) {
            return;
        }

        storage.saveUrls(failedLinks,
                Utils.SDF.format(System.currentTimeMillis()) + "_failed_links_size_"
                        + failedLinks.size() + ".txt");
        log.info("Failed links: " + failedLinks.size());
    }

}
