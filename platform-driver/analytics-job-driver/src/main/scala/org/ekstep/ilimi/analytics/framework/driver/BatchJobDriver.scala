package org.ekstep.ilimi.analytics.framework.driver

import org.ekstep.ilimi.analytics.framework.DataFetcher
import org.ekstep.ilimi.analytics.framework.DataFilter
import org.ekstep.ilimi.analytics.framework.JobConfig
import org.ekstep.ilimi.analytics.framework.JobRunner
import org.ekstep.ilimi.analytics.framework.OutputDispatcher
import org.ekstep.ilimi.analytics.framework.util.CommonUtil
import org.ekstep.ilimi.analytics.framework.JobContext
import org.ekstep.ilimi.analytics.framework.Event

/**
 * @author Santhosh
 */
object BatchJobDriver {

    def process(config: JobConfig) {

        JobContext.parallelization = CommonUtil.getParallelization(config);
        val sc = CommonUtil.getSparkContext(JobContext.parallelization, config.appName.getOrElse(config.model));
        val rdd = DataFetcher.fetchBatchData[Event](sc, config.search);
        JobContext.deviceMapping = rdd.filter { x => CommonUtil.getEventId(x).equals("GE_GENIE_START") }.map { x => (x.did.get, x.edata.eks.loc.getOrElse("")) }.collect().toMap;
        val filterRdd = DataFilter.filterAndSort(rdd, config.filters, config.sort);
        val output = JobRunner.executeBatch(config.model, sc, filterRdd, config.modelParams);
        OutputDispatcher.dispatch(config.output, output);
        CommonUtil.closeSparkContext(sc);
    }
}