package fi.csc.termed.search.controller;

import fi.csc.termed.search.dto.AffectedNodes;
import fi.csc.termed.search.dto.TermedNotification;
import fi.csc.termed.search.dto.TermedNotification.Node;
import fi.csc.termed.search.service.ElasticSearchService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@RestController
public class NotificationController {

    private final ElasticSearchService elasticSearchService;

    private final Object lock = new Object();
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final List<String> conceptTypes = singletonList("Concept");
    private static final List<String> vocabularyTypes = asList("TerminologicalVocabulary", "Vocabulary");

    @Autowired
    public NotificationController(ElasticSearchService elasticSearchService) {
        this.elasticSearchService = elasticSearchService;
    }

    @RequestMapping("/notify")
    public void notify(@RequestBody TermedNotification notification) throws IOException, InterruptedException {
        log.debug("Notification received");

        synchronized(this.lock) {

            Map<String, List<Node>> nodesByGraphId =
                    notification.getBody().getNodes().stream().collect(Collectors.groupingBy(node -> node.getType().getGraph().getId()));

            for (Map.Entry<String, List<Node>> entries : nodesByGraphId.entrySet()) {

                String graphId = entries.getKey();
                List<Node> nodes = entries.getValue();

                List<String> vocabularies = extractIdsOfType(nodes, vocabularyTypes);
                List<String> concepts = extractIdsOfType(nodes, conceptTypes);

                switch (notification.getType()) {
                    case NodeSavedEvent:
                        this.elasticSearchService.updateIndexAfterUpdate(new AffectedNodes(graphId, vocabularies, concepts));
                        break;
                    case NodeDeletedEvent:
                        this.elasticSearchService.updateIndexAfterDelete(new AffectedNodes(graphId, vocabularies, concepts));
                        break;
                }
            }
        }
    }

    private static @NotNull List<String> extractIdsOfType(@NotNull List<Node> nodes, @NotNull List<String> types) {
        return nodes.stream()
                .filter(node -> types.contains(node.getType().getId()))
                .map(Node::getId)
                .collect(toList());
    }
}
