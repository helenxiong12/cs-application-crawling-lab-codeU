package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
		if (queue.isEmpty()) {
            return null;
        }
    	/*
			Choose and remove a URL from the queue in FIFO order.
			Read the contents of the page using WikiFetcher.readWikipedia, which reads cached copies of pages we have included in this repository for testing purposes (to avoid problems if the Wikipedia version changes).
			It should index pages regardless of whether they are already indexed.
			It should find all the internal links on the page and add them to the queue in the order they appear. "Internal links" are links to other Wikipedia pages.
			And it should return the URL of the page it indexed.
    	*/
        String url = queue.poll();
		// System.out.println("Crawling " + url);
		Elements paragraphs;
		if (testing == false && index.isIndexed(url)) {
			return null;
		}
		if (testing) {
			// System.out.print("reading: ");
			// System.out.println(url);
			paragraphs = wf.readWikipedia(url);
		} else {
			// System.out.print("fetching: ");
			// System.out.println(url);
            paragraphs = wf.fetchWikipedia(url);
        }
		index.indexPage(url, paragraphs);
		queueInternalLinks(paragraphs);
		return url;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        for (Element paragraph : paragraphs) {
        	queueInternalLinks(paragraph);
        }
	}

	private void queueInternalLinks(Element paragraph) {
		Elements elements = paragraph.select("a[href]");
		for (Element e : elements) {
			String relURL = e.attr("href");
			// System.out.println("relURL = " + relURL);
			if (relURL.startsWith("/wiki/")) {
				// String realURL = e.attr("abs:href");
				String realURL = "https://en.wikipedia.org" + relURL;
				// System.out.println("absURL = " + realURL);
				queue.offer(realURL);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            //break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
