if (sys.nframe() > 0) {
  script.dir <- dirname(sys.frame(1)$ofile)
  setwd(script.dir)
}

library(ggplot2)
library(plyr)

load_data <- function(filename) {
  d = read.csv(filename, header=FALSE, col.names=c("Bench", "Threads", "VM", "Value"), sep=";")
  n = length(d$Value)
  d$VM = factor(d$VM)
  d$VM = revalue(d$VM, c("FastLayoutLock"="LightweightLayoutLock"))
  #d$Iteration = seq(from = 1, to = n)
  d
}

full = load_data("conc_appends_1_32.csv")
full = load_data("conc_appends_1_64.csv")
full = load_data("conc_write_reads_ops_1_64.csv")
full = load_data("conc_write_reads_ops_v2_1_64.csv")

full = load_data("conc_write_reads_sep.csv")
full = load_data("conc_write_reads_sep_fair.csv")
full = load_data("conc_write_reads_1numa.csv")
full = load_data("conc_write_reads_1numa_16gb_heap.csv")
# full = load_data("conc_write_reads_2numa_16gb_heap.csv")
# full = load_data("conc_write_reads_2numa_10s_16gb_heap.csv")
# full = load_data("bench_array_conc_write_reads_arie.csv")
# full = load_data("java_write_read_ops.csv")
# java = load_data("java_write_read_ops2numa.csv")
# full = rbind(full, java)
full = load_data("conc_write_reads_ops_1numa.csv")
full = load_data("conc_write_reads_ops_2numa.csv")
full = load_data("conc_write_reads_ops_2numa_all.csv")
full = load_data("conc_write_reads_ops_2numa_2.csv")
full = load_data("conc_write_reads_ops_2numa_3.csv")
full = load_data("conc_write_reads_1_64_goliath.csv")
full = load_data("conc_write_reads_1_64_goliath_global_fll.csv")
full = load_data("conc_write_reads_1_64_goliath_global_fll1.csv")
full = load_data("conc_write_reads_reads_10_90_1_64_goliath_global_fll1.csv")
full = load_data("conc_write_reads_10_90_1.csv")
full = load_data("conc_write_reads_10_90_2.csv")
full = load_data("conc_write_reads_10_90_5.csv")
full = load_data("conc_write_reads_10_90_6.csv")
full = load_data("conc_write_ops_1.csv")
full = load_data("conc_write_ops_2.csv")
full = load_data("conc_write_reads_10_90_8.csv")
full = load_data("conc_reads_ops1.csv")

base = max(subset(full, Threads=="1")$Value)
# base = max(subset(full, Threads=="2")$Value)/2
base_fll = subset(full, VM=="LightweightLayoutLock" & Threads=="1")$Value
# full$Value = full$Value / base

# full = subset(full, VM != "FixedSize")
# full = subset(full, Threads != "1")

# full = load_data("monte_carlo_pi.csv")
# full = load_data("monte_carlo_pi_rb.csv")
# full = load_data("monte_carlo_pi_sparc4x.csv")
# full = load_data("mandelbrot_shared1.csv")
# full$Value = 1 / full$Value
# full$Value = full$Value / subset(full, Threads=="1")$Value
# base = base_fll = 1

#png(file = "times.png", width=740, height=400)
ggplot(data = full, aes(x=Threads, y=Value, group=VM, color=VM)) + geom_point() + geom_line() +
  xlab("Threads") + ylab("Throughput") +
  scale_x_continuous(breaks = c(0, 1, 2, 4, 8, 12, 16, 18, 20, 24, 28, 32, 36, 48), minor_breaks = NULL, limits = c(0, max(full$Threads))) +
  scale_y_continuous(limits = c(0, max(full$Value))) +
  #scale_y_continuous(breaks = seq(0, max(full$Value), 10000)) +
  theme(text = element_text(size=20), legend.position="bottom",
        legend.title = element_blank(), legend.background = element_blank(), legend.key = element_blank(),
        legend.text = element_text(size = 16)) +
  geom_abline(slope = base) +
  geom_abline(slope = base_fll)
  
#   scale_y_continuous(breaks = c(seq(0, 200, 30), 20, 40), minor_breaks = NULL) +
#   scale_x_continuous(breaks = seq(0, 3000, 600)) +
#dev.off()

#png(file = "compare.png", width=740, height=200)geom_abline
# ggplot(data = full, aes(x=Threads, y=Value, color=VM)) + geom_boxplot() + ylab("FPS") + xlab("") +
#   theme(text = element_text(size=20)) +
#   scale_y_continuous(breaks = seq(0, 180, 30), minor_breaks = NULL) +
#   theme(legend.position="none", axis.title.y = element_blank()) +
#   coord_flip()
#dev.off()

# ggplot(data = full, aes(x=VM, y=Value, color=VM)) + geom_boxplot() + ylab("FPS") + theme(text = element_text(size=20))

#png(file = "boxplot.png", width=1200, height=300)

#dev.off()
