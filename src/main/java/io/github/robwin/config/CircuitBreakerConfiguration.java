package io.github.robwin.config;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.configure.*;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.consumer.DefaultEventConsumerRegistry;
import io.github.resilience4j.consumer.EventConsumerRegistry;
import io.github.resilience4j.core.registry.CompositeRegistryEventConsumer;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.fallback.FallbackDecorators;
import io.github.resilience4j.spelresolver.SpelResolver;
import io.github.resilience4j.utils.AspectJOnClasspathCondition;
import io.github.resilience4j.utils.ReactorOnClasspathCondition;
import io.github.resilience4j.utils.RxJava2OnClasspathCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
public class CircuitBreakerConfiguration {

    private final CircuitBreakerProperties circuitBreakerProperties;

    public CircuitBreakerConfiguration(
            CircuitBreakerProperties circuitBreakerProperties) {
        this.circuitBreakerProperties = circuitBreakerProperties;
    }

    @Bean
    @Qualifier("compositeCircuitBreakerCustomizer")
    public CompositeCustomizer<CircuitBreakerConfigCustomizer> compositeCircuitBreakerCustomizer(
            @Autowired(required = false) List<CircuitBreakerConfigCustomizer> customizers) {
        return new CompositeCustomizer<>(customizers);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            EventConsumerRegistry<CircuitBreakerEvent> eventConsumerRegistry,
            RegistryEventConsumer<CircuitBreaker> circuitBreakerRegistryEventConsumer,
            @Qualifier("compositeCircuitBreakerCustomizer") CompositeCustomizer<CircuitBreakerConfigCustomizer> compositeCircuitBreakerCustomizer) {

        CircuitBreakerRegistry circuitBreakerRegistry = createCircuitBreakerRegistry(
                circuitBreakerProperties, circuitBreakerRegistryEventConsumer,
                compositeCircuitBreakerCustomizer);
        registerEventConsumer(circuitBreakerRegistry, eventConsumerRegistry);
        // then pass the map here
        initCircuitBreakerRegistry(circuitBreakerRegistry, compositeCircuitBreakerCustomizer);
        return circuitBreakerRegistry;
    }

    @Bean
    @Primary
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerRegistryEventConsumer(
            Optional<List<RegistryEventConsumer<CircuitBreaker>>> optionalRegistryEventConsumers) {
        return new CompositeRegistryEventConsumer<>(
                optionalRegistryEventConsumers.orElseGet(ArrayList::new));
    }

    @Bean
    @Conditional(value = {AspectJOnClasspathCondition.class})
    public CircuitBreakerAspect circuitBreakerAspect(
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) List<CircuitBreakerAspectExt> circuitBreakerAspectExtList,
            FallbackDecorators fallbackDecorators,
            SpelResolver spelResolver
    ) {
        return new CircuitBreakerAspect(circuitBreakerProperties, circuitBreakerRegistry,
                circuitBreakerAspectExtList, fallbackDecorators, spelResolver);
    }


    @Bean
    @Conditional(value = {RxJava2OnClasspathCondition.class, AspectJOnClasspathCondition.class})
    public RxJava2CircuitBreakerAspectExt rxJava2CircuitBreakerAspect() {
        return new RxJava2CircuitBreakerAspectExt();
    }

    @Bean
    @Conditional(value = {ReactorOnClasspathCondition.class, AspectJOnClasspathCondition.class})
    public ReactorCircuitBreakerAspectExt reactorCircuitBreakerAspect() {
        return new ReactorCircuitBreakerAspectExt();
    }

    /**
     * The EventConsumerRegistry is used to manage EventConsumer instances. The
     * EventConsumerRegistry is used by the CircuitBreakerHealthIndicator to show the latest
     * CircuitBreakerEvents events for each CircuitBreaker instance.
     *
     * @return a default EventConsumerRegistry {@link io.github.resilience4j.consumer.DefaultEventConsumerRegistry}
     */
    @Bean
    public EventConsumerRegistry<CircuitBreakerEvent> eventConsumerRegistry() {
        return new DefaultEventConsumerRegistry<>();
    }

    /**
     * Initializes a circuitBreaker registry.
     *
     * @param circuitBreakerProperties The circuit breaker configuration properties.
     * @param customizerMap
     * @return a CircuitBreakerRegistry
     */
    CircuitBreakerRegistry createCircuitBreakerRegistry(
            CircuitBreakerProperties circuitBreakerProperties,
            RegistryEventConsumer<CircuitBreaker> circuitBreakerRegistryEventConsumer,
            CompositeCustomizer<CircuitBreakerConfigCustomizer> customizerMap) {

        Map<String, CircuitBreakerConfig> configs = circuitBreakerProperties.getConfigs()
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> circuitBreakerProperties
                                .createCircuitBreakerConfig(entry.getKey(), entry.getValue(),
                                        customizerMap)));

        return CircuitBreakerRegistry.of(configs, circuitBreakerRegistryEventConsumer,
                io.vavr.collection.HashMap.ofAll(circuitBreakerProperties.getTags()));
    }

    /**
     * Initializes the CircuitBreaker registry.
     *
     * @param circuitBreakerRegistry The circuit breaker registry.
     * @param customizerMap
     */
    void initCircuitBreakerRegistry(CircuitBreakerRegistry circuitBreakerRegistry,
                                    CompositeCustomizer<CircuitBreakerConfigCustomizer> customizerMap) {
        circuitBreakerProperties.getInstances().forEach(
                (name, properties) -> circuitBreakerRegistry.circuitBreaker(name,
                        circuitBreakerProperties
                                .createCircuitBreakerConfig(name, properties, customizerMap))
        );
    }

    /**
     * Registers the post creation consumer function that registers the consumer events to the
     * circuit breakers.
     *
     * @param circuitBreakerRegistry The circuit breaker registry.
     * @param eventConsumerRegistry  The event consumer registry.
     */
    public void registerEventConsumer(CircuitBreakerRegistry circuitBreakerRegistry,
                                      EventConsumerRegistry<CircuitBreakerEvent> eventConsumerRegistry) {
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> registerEventConsumer(eventConsumerRegistry, event.getAddedEntry()))
                .onEntryReplaced(event -> registerEventConsumer(eventConsumerRegistry, event.getNewEntry()));
    }

    private void registerEventConsumer(
            EventConsumerRegistry<CircuitBreakerEvent> eventConsumerRegistry,
            CircuitBreaker circuitBreaker) {
        int eventConsumerBufferSize = circuitBreakerProperties
                .findCircuitBreakerProperties(circuitBreaker.getName())
                .map(io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigurationProperties.InstanceProperties::getEventConsumerBufferSize)
                .orElse(100);
        circuitBreaker.getEventPublisher().onEvent(eventConsumerRegistry
                .createEventConsumer(circuitBreaker.getName(), eventConsumerBufferSize));
    }
}


