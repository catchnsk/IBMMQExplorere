package com.ibmexplorer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.MQMessage;
import com.ibmexplorer.dto.response.MessageDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqMessageParserService {

    private final ObjectMapper objectMapper;

    public MessageDetailResponse parseMessage(MQMessage mqMessage, byte[] rawBody) {
        Charset charset = detectCharset(mqMessage.characterSet);
        String textContent;
        try {
            textContent = new String(rawBody, charset);
        } catch (Exception e) {
            textContent = new String(rawBody, StandardCharsets.ISO_8859_1);
        }

        String contentType = detectContentType(rawBody, mqMessage.format);
        String jsonView = null;
        String xmlView = null;

        if ("JSON".equals(contentType)) {
            jsonView = prettyPrintJson(textContent);
        } else if ("XML".equals(contentType)) {
            xmlView = prettyPrintXml(textContent);
        }

        LocalDateTime putTime = null;
        if (mqMessage.putDateTime != null) {
            putTime = mqMessage.putDateTime.getTime().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        return MessageDetailResponse.builder()
            .messageId(HexFormat.of().formatHex(mqMessage.messageId))
            .correlationId(HexFormat.of().formatHex(mqMessage.correlationId))
            .format(mqMessage.format != null ? mqMessage.format.trim() : "")
            .encoding(mqMessage.encoding)
            .codedCharacterSetId(mqMessage.characterSet)
            .messageType(mqMessage.messageType)
            .expiry(mqMessage.expiry)
            .priority(mqMessage.priority)
            .persistence(mqMessage.persistence)
            .replyToQueue(mqMessage.replyToQueueName != null ? mqMessage.replyToQueueName.trim() : "")
            .replyToQueueManager(mqMessage.replyToQueueManagerName != null ? mqMessage.replyToQueueManagerName.trim() : "")
            .putApplicationName(mqMessage.putApplicationName != null ? mqMessage.putApplicationName.trim() : "")
            .putDateTime(putTime)
            .userId(mqMessage.userId != null ? mqMessage.userId.trim() : "")
            .rawBodySize(rawBody.length)
            .contentType(contentType)
            .textView(textContent)
            .jsonView(jsonView)
            .xmlView(xmlView)
            .hexView(toHexDump(rawBody))
            .build();
    }

    private String detectContentType(byte[] body, String format) {
        if (body == null || body.length == 0) return "EMPTY";

        String fmt = format != null ? format.trim() : "";
        if ("MQSTR".equals(fmt) || fmt.isEmpty()) {
            String sample = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8).trim();
            if (sample.startsWith("{") || sample.startsWith("[")) return "JSON";
            if (sample.startsWith("<")) return "XML";
            return "TEXT";
        }

        // Sniff raw bytes
        byte first = body[0];
        if (first == '{' || first == '[') return "JSON";
        if (first == '<') return "XML";

        return "BINARY";
    }

    private String prettyPrintJson(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return json; // Return as-is if not valid JSON
        }
    }

    private String prettyPrintXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return xml; // Return as-is if not valid XML
        }
    }

    private String toHexDump(byte[] data) {
        if (data == null || data.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 16) {
            sb.append(String.format("%08X  ", i));
            // Hex bytes
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    sb.append(String.format("%02X ", data[i + j]));
                } else {
                    sb.append("   ");
                }
                if (j == 7) sb.append(" ");
            }
            sb.append(" |");
            // ASCII representation
            for (int j = 0; j < 16 && i + j < data.length; j++) {
                byte b = data[i + j];
                sb.append((b >= 32 && b < 127) ? (char) b : '.');
            }
            sb.append("|\n");
        }
        return sb.toString();
    }

    private Charset detectCharset(int ccsid) {
        return switch (ccsid) {
            case 819, 1208 -> StandardCharsets.UTF_8;
            case 1200, 1201 -> StandardCharsets.UTF_16;
            case 850, 858   -> Charset.forName("IBM850");
            case 37         -> Charset.forName("IBM037");
            case 500        -> Charset.forName("IBM500");
            default         -> StandardCharsets.UTF_8;
        };
    }
}
