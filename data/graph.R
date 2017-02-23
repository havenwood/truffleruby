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
  #d$Iteration = seq(from = 1, to = n)
  d
}

full = load_data("conc_appends_1_32.csv")

#png(file = "times.png", width=740, height=400)
ggplot(data = full, aes(y=Value, x=Threads)) + geom_point(aes(color=VM)) +
  xlab("Threads") + ylab("Throughput") +
  scale_x_continuous(breaks = 2^(0:5), minor_breaks = NULL) +
  scale_y_continuous(breaks = seq(0, max(full$Value), 10000)) +
  theme(text = element_text(size=20), legend.position="bottom",
        legend.title = element_blank(), legend.background = element_blank(), legend.key = element_blank(),
        legend.text = element_text(size = 16))

#   scale_y_continuous(breaks = c(seq(0, 200, 30), 20, 40), minor_breaks = NULL) +
#   scale_x_continuous(breaks = seq(0, 3000, 600)) +
#dev.off()

#png(file = "compare.png", width=740, height=200)
ggplot(data = full, aes(x=Threads, y=Value, color=VM)) + geom_boxplot() + ylab("FPS") + xlab("") +
  theme(text = element_text(size=20)) +
  scale_y_continuous(breaks = seq(0, 180, 30), minor_breaks = NULL) +
  theme(legend.position="none", axis.title.y = element_blank()) +
  coord_flip()
#dev.off()

# ggplot(data = full, aes(x=VM, y=Value, color=VM)) + geom_boxplot() + ylab("FPS") + theme(text = element_text(size=20))

#png(file = "boxplot.png", width=1200, height=300)

#dev.off()
