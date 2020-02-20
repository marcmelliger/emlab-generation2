/*******************************************************************************
 * Copyright 2013 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package emlab.gen.role.tender;

import emlab.gen.domain.contract.CashFlow;
import emlab.gen.domain.market.electricity.ElectricitySpotMarket;
import emlab.gen.domain.market.electricity.PowerPlantDispatchPlan;
import emlab.gen.domain.market.electricity.SegmentLoad;
import emlab.gen.domain.policy.renewablesupport.RenewableSupportSchemeTender;
import emlab.gen.domain.policy.renewablesupport.TenderBid;
import emlab.gen.domain.policy.renewablesupport.TenderClearingPoint;
import emlab.gen.domain.technology.PowerPlant;
import emlab.gen.engine.AbstractRole;
import emlab.gen.engine.Role;
import emlab.gen.repository.Reps;

/**
 * @author rjjdejeu
 *
 */

public class OrganizeRenewableTenderPaymentsRole extends AbstractRole<RenewableSupportSchemeTender>
        implements Role<RenewableSupportSchemeTender> {

    Reps reps;

    @Override
    public void act(RenewableSupportSchemeTender scheme) {

        double annualTenderRevenue = 0d;

        for (TenderBid currentTenderBid : reps.findAllTenderBidsThatShouldBePaidInTimeStep(scheme,
                getCurrentTick())) {

            TenderClearingPoint tenderClearingPoint = reps.findOneClearingPointForTimeAndRenewableSupportSchemeTender(currentTenderBid.getTime(), scheme);

            annualTenderRevenue = currentTenderBid.getAcceptedAmount() * tenderClearingPoint.getPrice();
            // logger.warn("Accepted Amount " +
            // currentTenderBid.getAcceptedAmount() + "tenderClearingPoint"
            // + tenderClearingPoint.getPrice());
            // Is the accepted amount generated every year?
            double annualRevenueFromElectricityMarket = 0;
            if (scheme.isExpostRevenueCalculation() == true) {
                annualRevenueFromElectricityMarket = computeRevenueFromElectricityMarket(scheme, currentTenderBid);
                annualTenderRevenue = annualTenderRevenue - annualRevenueFromElectricityMarket;
                // if (annualTenderRevenue < 0)
                // annualTenderRevenue = 0;

            } else {
                double tenderClearingPrice = 0d;
                tenderClearingPrice = (Double.isNaN(tenderClearingPoint.getPrice())) ? 0d
                        : tenderClearingPoint.getPrice();
                annualTenderRevenue = currentTenderBid.getAcceptedAmount() * tenderClearingPoint.getPrice();
            }

            reps.createCashFlow(scheme.getRegulator(), currentTenderBid.getBidder(),
                    annualTenderRevenue, CashFlow.TENDER_SUBSIDY, getCurrentTick(), currentTenderBid.getPowerPlant());

            // logger.warn("Producer's cash reserves after payment of tender
            // subsidy"
            // + currentTenderBid.getBidder().getCash());

        }
        // logger.warn("____PAYMENT ROLE____ annualTenderRevenue" +
        // annualTenderRevenue);

    }

    private double computeRevenueFromElectricityMarket(RenewableSupportSchemeTender scheme, TenderBid bid) {

        double sumEMR = 0d;
        double emAvgPriceBasedRevenue = 0d;
        double electricityPrice = 0d;
        double totalGenerationOfPlantInMwh = 0d;
        double totalAnnualHoursOfGeneration = 0d;
        double sumRevenueOfElectricity = 0d;
        // the for loop below calculates the electricity
        // market
        // price the plant earned
        // throughout the year, for its total production
        PowerPlant plant = bid.getPowerPlant();
        ElectricitySpotMarket eMarket = reps.findElectricitySpotMarketForZone(scheme.getRegulator().getZone());

        for (SegmentLoad segmentLoad : eMarket.getLoadDurationCurve()) {
            // logger.warn("Inside segment loop for
            // calculating
            // total production");

            electricityPrice = reps.findOneSegmentClearingPointForMarketSegmentAndTime(
                    getCurrentTick(), segmentLoad.getSegment(), eMarket, false).getPrice();
            double hours = segmentLoad.getSegment().getLengthInHours();
            totalAnnualHoursOfGeneration += hours;
            sumRevenueOfElectricity += electricityPrice * hours;

            PowerPlantDispatchPlan ppdp = reps
                    .findOnePowerPlantDispatchPlanForPowerPlantForSegmentForTime(plant, segmentLoad.getSegment(),
                            getCurrentTick(), false);

            if (ppdp == null || ppdp.getStatus() < 0) {
                electricityPrice = 0d;
            } else if (ppdp.getStatus() >= 2) {
                // do a sensitivity here to different
                // averages of electricity prices.
                sumEMR = sumEMR + electricityPrice * hours * ppdp.getAcceptedAmount();
                totalGenerationOfPlantInMwh += hours * ppdp.getAcceptedAmount();
            }

        }

        emAvgPriceBasedRevenue = totalGenerationOfPlantInMwh * (sumRevenueOfElectricity / totalAnnualHoursOfGeneration);

        if (scheme.isRevenueByAverageElectricityPrice() == true)
            return emAvgPriceBasedRevenue;
        else
            return sumEMR;

    }

}