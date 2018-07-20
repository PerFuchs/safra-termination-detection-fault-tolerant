import csv
import sys
from collections import defaultdict

from os import listdir

from os.path import isdir, basename, isfile

import plotly
import plotly.graph_objs as go

# Create random data with numpy
import numpy as np

import scipy.stats as st

from graphing import get_scatter_graph_with_mean_and_confidence_interval, get_box_trace

experiment_folder = sys.argv[1]


class MyDialect(csv.excel):
    delimiter = ';'


class Repetition:

    def read_warning_file(self):
        warningFileName = '/'.join([self.folder, '.warning'])
        if isfile(warningFileName):
            return open(warningFileName).readlines()
        return []

    def read_error_file(self):
        errorFileName = '/'.join([self.folder, '.error'])
        if isfile(errorFileName):
            return open(errorFileName).readlines()
        return []

    def __init__(self, folder, number_of_nodes):
        self.folder = folder
        self.number = int(basename(self.folder))
        self.errors = self.read_error_file()
        self.valid = len(self.errors) == 0

        self.warnings = self.read_warning_file()

        with open('/'.join((folder, 'safraStatistics.csv'))) as csvFile:
            reader = csv.DictReader(csvFile, dialect=MyDialect())
            for statistics in reader:
                self.tokens = int(statistics['tokens'])
                self.tokens_after_termination = int(statistics['tokenAfterTermination'])
                self.backup_tokens = int(statistics['backupToken'])
                self.token_bytes = int(statistics['tokenSize (bytes)'])
                self.safra_time = float(statistics['safraTime (seconds)'])
                self.safra_time_after_termination = float(statistics['safraTimeAfterTermination'])
                self.total_time = float(statistics['totalTime'])
                if 'numberOfNodesCrashed' in statistics:
                    self.number_of_nodes_crashed = int(statistics['numberOfNodesCrashed'])
                else:
                    self.number_of_nodes_crashed = -1
                break

        logs = len(list(filter(lambda f: f.endswith('.log'), listdir(folder))))
        chandy_misra_results = len(list(filter(lambda f: f.endswith('.chandyMisra'), listdir(folder))))

        assert logs == number_of_nodes + 1  # 1 is the out.log file summarizing the whole run
        assert chandy_misra_results == number_of_nodes


class Configuration:
    def __init__(self, folder):
        configuration_name = basename(folder)
        number_of_nodes, fault_percentage, _ = configuration_name.split('-')
        self.number_of_nodes = int(number_of_nodes)
        self.fault_percentage = int(fault_percentage) / 100

        self.repetitions = []
        self.invalid_repetitions = []
        for fileName in listdir(folder):
            if isdir('/'.join((folder, fileName))):
                r = Repetition('/'.join((folder, fileName)), self.number_of_nodes)
                if r.valid:
                    self.repetitions.append(r)
                else:
                    self.invalid_repetitions.append(r)

    def get_tokens(self):
        return list(map(lambda r: r.tokens, self.repetitions))

    def get_safra_times(self):
        return list(map(lambda r: r.safra_time, self.repetitions))

    def get_number_of_nodes_crashed(self):
        return list(map(lambda r: r.number_of_nodes_crashed, self.repetitions))

    def get_backup_tokens(self):
        return list(map(lambda r: r.backup_tokens, self.repetitions))

    def get_tokens_after_termination(self):
        return list(map(lambda r: r.tokens_after_termination, self.repetitions))


configurations = []

for file_name in listdir(experiment_folder):
    configuration_folder = '/'.join((experiment_folder, file_name))
    if isdir(configuration_folder) and configuration_folder.endswith('.run'):
        configuration = Configuration(configuration_folder)
        configurations.append(configuration)

        print("Repetitions: %i Invalid Repetitions: %i" % (len(configuration.repetitions), len(configuration.invalid_repetitions)))

        print("Errors:")
        for r in configuration.invalid_repetitions:
            print("Repetition: %i" % r.number)
            for e in r.errors:
                print("  " + e)
        print("")
        print("")

fields = ['tokens', 'tokens_after_termination', 'backup_tokens', 'number_of_nodes_crashed']
data = defaultdict(lambda: list())

for f in fields:
    for c in configurations:
        print(dir(c))
        data[f].append(get_box_trace(getattr(c, 'get_'+f)(), c.number_of_nodes))

for plot_name, plot_data in data.items():
    plotly.offline.plot(plot_data, filename='%s.html' % plot_name)


# data = get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens(), "tokens")
#
#
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_number_of_node_crashed(), "faults")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_backup_tokens(), "backup")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens_after_termination(), "afterTermination")
# data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_safra_times(), "times")
# plotly.offline.plot(data, filename='graph.html')

