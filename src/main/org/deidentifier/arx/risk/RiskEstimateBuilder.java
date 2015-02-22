/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2015 Florian Kohlmayer, Fabian Prasser
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deidentifier.arx.risk;

import java.util.Set;

import org.deidentifier.arx.ARXPopulationModel;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.risk.RiskModelPopulationBasedUniquenessRisk.StatisticalModel;

/**
 * A builder for risk estimates
 * @author Fabian Prasser
 *
 */
public class RiskEstimateBuilder {
    
    /**
     * Helper class
     * @author Fabian Prasser
     *
     */
    static final class ComputationInterruptedException extends RuntimeException {
        private static final long serialVersionUID = -4553285212475615392L;
    }
    
    /**
     * Helper class
     * @author Fabian Prasser
     */
    static final class WrappedBoolean {
        public boolean value = false;
    }

    /** Fields */
    private final ARXPopulationModel          population;
    /** Fields */
    private final DataHandle                  handle;
    /** Fields */
    private final Set<String>                 identifiers;
    /** Classes */
    private RiskModelEquivalenceClasses classes;
    /** Asynchronous computation*/
    private final WrappedBoolean              stop;

    /**
     * Creates a new instance
     * @param population
     * @param handle
     * @param classes
     */
    public RiskEstimateBuilder(ARXPopulationModel population, DataHandle handle, RiskModelEquivalenceClasses classes) {
        this(population, handle, null, classes);
    }

    /**
     * Creates a new instance
     * @param population
     * @param handle
     * @param identifiers
     */
    public RiskEstimateBuilder(ARXPopulationModel population, DataHandle handle, Set<String> identifiers) {
        this(population, handle, identifiers, (RiskModelEquivalenceClasses)null);
    }
    /**
     * Creates a new instance
     * @param population
     * @param handle
     * @param identifiers
     */
    private RiskEstimateBuilder(ARXPopulationModel population, DataHandle handle, RiskModelEquivalenceClasses classes, WrappedBoolean stop) {
        this.population = population;
        this.handle = handle;
        this.identifiers = null;
        this.classes = classes;
        synchronized(this) {
            this.stop = stop;
        }
    }
    
    /**
     * Creates a new instance
     * @param population
     * @param handle
     * @param qi
     * @param classes
     */
    private RiskEstimateBuilder(ARXPopulationModel population, 
                                DataHandle handle, 
                                Set<String> identifiers, 
                                RiskModelEquivalenceClasses classes) {
        this.population = population;
        this.handle = handle;
        this.identifiers = identifiers;
        this.classes = classes;
        synchronized(this) {
            stop = new WrappedBoolean();
        }
    }

    /**
     * Creates a new instance
     * @param population
     * @param handle
     * @param identifiers
     */
    private RiskEstimateBuilder(ARXPopulationModel population, DataHandle handle, Set<String> identifiers, WrappedBoolean stop) {
        this.population = population;
        this.handle = handle;
        this.identifiers = identifiers;
        this.classes = null;
        synchronized(this) {
            this.stop = stop;
        }
    }
    
    /**
     * Returns a model of the equivalence classes in this data set
     * @return
     */
    public RiskModelEquivalenceClasses getEquivalenceClassModel() {
        synchronized(this) {
            if (classes == null) {
                classes = new RiskModelEquivalenceClasses(handle, identifiers, stop);
            }
            return classes;
        }
    }
    
    /**
     * Returns an interruptible instance of this object.
     *
     * @return
     */
    public RiskEstimateBuilderInterruptible getInterruptibleInstance(){
        return new RiskEstimateBuilderInterruptible(this);
    }

    /**
     * Returns a class providing access to population-based risk estimates about the attributes.
     * Uses the decision rule by Dankar et al., excluding the SNB model
     * @return
     */
    public RiskModelAttributes getPopulationBasedAttributeRisks() {
       return getAttributeRisks(null);
    }

    /**
     * Returns a class providing access to population-based risk estimates about the attributes.
     * @param model Uses the given statistical model
     * @return
     */
    public RiskModelAttributes getPopulationBasedAttributeRisks(StatisticalModel model) {
       return getAttributeRisks(model);
    }
    
    /**
     * Returns a class providing population-based uniqueness estimates
     * @return
     */
    public RiskModelPopulationBasedUniquenessRisk getPopulationBasedUniquenessRisk(){
        return new RiskModelPopulationBasedUniquenessRisk(population, getEquivalenceClassModel(), stop);
    }
    
    
    /**
     * Returns a class providing access to sample-based risk estimates about the attributes
     * @return
     */
    public RiskModelAttributes getSampleBasedAttributeRisks() {
       return getAttributeRisks(null);
    }

    /**
     * Returns a class providing sample-based re-identification risk estimates
     * @return
     */
    public RiskModelSampleBasedReidentificationRisk getSampleBasedReidentificationRisk(){
        return new RiskModelSampleBasedReidentificationRisk(getEquivalenceClassModel());
    }

    /**
     * Returns a class providing sample-based uniqueness estimates
     * @return
     */
    public RiskModelSampleBasedUniquenessRisk getSampleBasedUniquenessRisk(){
        return new RiskModelSampleBasedUniquenessRisk(getEquivalenceClassModel());
    }

    /**
     * Returns a class providing access to population- or sample-based risk estimates about the attributes
     * @param model null for sample-based model
     * @return
     */
    private RiskModelAttributes getAttributeRisks(final StatisticalModel model) {
        return new RiskModelAttributes(this.identifiers, this.stop) {
            @Override
            protected RiskProvider getRiskProvider(final Set<String> attributes, 
                                                   final WrappedBoolean stop) {
                
                // Compute classes
                RiskEstimateBuilder builder = new RiskEstimateBuilder(population,
                                                                      handle,
                                                                      attributes,
                                                                      stop);
                RiskModelEquivalenceClasses classes = builder.getEquivalenceClassModel();
                builder = new RiskEstimateBuilder(population, handle, classes, stop);
                
                
                // Use classes to compute risks
                final RiskModelSampleBasedReidentificationRisk reidentificationRisks = builder.getSampleBasedReidentificationRisk();
                final double highestRisk = reidentificationRisks.getHighestRisk();
                final double averageRisk = reidentificationRisks.getAverageRisk();
                final double fractionOfUniqueTuples;
                if (model == null) {
                    fractionOfUniqueTuples = builder.getSampleBasedUniquenessRisk().getFractionOfUniqueTuples();
                } else {
                    fractionOfUniqueTuples = builder.getPopulationBasedUniquenessRisk().getFractionOfUniqueTuples(model);
                }

                // Return a provider
                return new RiskProvider() {
                    public double getAverageRisk() {
                        return averageRisk;
                    }
                    public double getFractionOfUniqueTuples() {
                        return fractionOfUniqueTuples;
                    }
                    public double getHighestRisk() {
                        return highestRisk;
                    }
                };
            }
        };
    }
    
    /**
     * Interrupts this instance
     */
    void interrupt() {
        synchronized(this) {
            this.stop.value = true;
        }
    }
}