package com.redhat.repository.validator;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:mjobanek@redhat.com">Matous Jobanek</a>
 */
public class OnlineRepositoryDiscovery {

    private ValidatorContext ctx;
    private XPathFactory xPathFactory;
    private DocumentBuilderFactory factory;
    private XPathExpression allLinksXPath;
    private List<String> repositoryEntities;

    public OnlineRepositoryDiscovery(ValidatorContext ctx) {
        this.ctx = ctx;
        repositoryEntities = new ArrayList<>();
        xPathFactory = XPathFactory.newInstance();
        factory = DocumentBuilderFactory.newInstance();
        try {
            allLinksXPath = xPathFactory.newXPath().compile("//tr/td/a[text()!='Parent Directory']/@href");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    public void discover() {
        try {

            List<String> rootLinks = retrieveAllLinks(ctx.getValidatedRepo());
            deepDive(rootLinks, true);

        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deepDive(List<String> links, boolean isRoot)
        throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {

        for (String link : links) {
            if (link.endsWith("/")) {
                deepDive(retrieveAllLinks(link), false);
            } else {
                System.out.println(link);
                repositoryEntities.add(link);
            }
            if (isRoot) {
                writeAllEntities();
            }
        }

    }

    private void writeAllEntities() throws IOException {
        for (String repositoryEntity : repositoryEntities) {
            FileUtils.touch(new File("repositoryEntities.txt"));
            Files.write(Paths.get("repositoryEntities.txt"), repositoryEntity.concat("\n").getBytes(),
                        StandardOpenOption.APPEND);
        }
        repositoryEntities.clear();
    }

    private List<String> retrieveAllLinks(String pageLocation)
        throws IOException, XPathExpressionException, ParserConfigurationException, SAXException {

        String pageContent = HttpClient.getEntityString(pageLocation);
        Document doc = parseAndGetDoc(pageContent);
        NodeList nodeList = (NodeList) allLinksXPath.evaluate(doc, XPathConstants.NODESET);
        List<String> links = new ArrayList<>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node linkRow = nodeList.item(i);
            links.add(linkRow.getTextContent());
        }
        return links;
    }

    private Document parseAndGetDoc(String content) throws ParserConfigurationException, IOException, SAXException {
        content = content.replaceAll("<link.*>", "");
        content = content.replaceAll("&nbsp;", "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(content)));
    }

}
