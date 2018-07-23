import csv
import sys
from collections import defaultdict

from os import listdir

from os.path import isdir, basename, isfile

import plotly
import plotly.graph_objs as go

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

    def __init__(self, folder, number_of_nodes, fault_percentage):
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
                self.token_bytes = int(statistics['tokenSize (bytes)']) / self.tokens
                self.safra_time = float(statistics['safraTime (seconds)'])
                self.safra_time_after_termination = float(statistics['safraTimeAfterTermination'])
                self.total_time = float(statistics['totalTime'])
                if 'numberOfNodesCrashed' in statistics:
                    self.number_of_nodes_crashed = int(statistics['numberOfNodesCrashed'])
                    real_fault_percentage = self.number_of_nodes_crashed / number_of_nodes
                    if real_fault_percentage < fault_percentage - 0.1:
                        self.valid = False
                        self.errors.append("Fault percentage of %f but aimed for %f" % (real_fault_percentage, fault_percentage))
                break  # There should be only one line

        logs = len(list(filter(lambda f: f.endswith('.log'), listdir(folder))))
        chandy_misra_results = len(list(filter(lambda f: f.endswith('.chandyMisra'), listdir(folder))))

        assert logs == number_of_nodes + 1  # 1 is the out.log file summarizing the whole run
        assert chandy_misra_results == number_of_nodes


class Configuration:
    def __init__(self, folder):
        configuration_name = basename(folder)
        number_of_nodes, fault_percentage, _ = configuration_name.split('-')
        self.number_of_nodes = int(number_of_nodes)
        if fault_percentage == "fs":
            self.fault_sensitive = True
            self.fault_percentage = 0.0
        else:
            self.fault_sensitive = False
            self.fault_percentage = int(fault_percentage) / 100

        self.repetitions = []
        self.invalid_repetitions = []
        for file_name in listdir(folder):
            if isdir('/'.join((folder, file_name))) and not file_name.endswith('.failure'):
                r = Repetition('/'.join((folder, file_name)), self.number_of_nodes, self.fault_percentage)
                if r.valid:
                    self.repetitions.append(r)
                else:
                    self.invalid_repetitions.append(r)

    def get_tokens(self):
        return list(map(lambda r: r.tokens, self.repetitions))

    def get_token_bytes(self):
        return list(map(lambda r: r.token_bytes, self.repetitions))

    def get_safra_time(self):
        return list(map(lambda r: r.safra_time, self.repetitions))

    def get_total_time(self):
        return list(map(lambda r: r.total_time, self.repetitions))

    def get_safra_time_after_termination(self):
        return list(map(lambda r: r.safra_time_after_termination, self.repetitions))

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
        # if (configuration.number_of_nodes == 200 and configuration.fault_percentage == 0.0):
        configurations.append(configuration)

configurations = sorted(configurations, key=lambda c: c.fault_percentage)
configurations = sorted(configurations, key=lambda c: c.number_of_nodes)

for c in configurations:
    print("+++++++++++++++++++++++++++++++++++++++++++++++++++")
    print("Nodes: %i Fault Percentage: %f" % (c.number_of_nodes, c.fault_percentage))
    print("Repetitions: %i \nInvalid Repetitions: %i" % (len(c.repetitions), len(c.invalid_repetitions)))

    print("Errors:")
    for r in c.invalid_repetitions:
        print("Repetition: %i" % r.number)
        for e in r.errors:
            print("  " + e)
    print("")
    print("")

fields = ['tokens', 'tokens_after_termination', 'backup_tokens', 'number_of_nodes_crashed', 'safra_time', 'safra_time_after_termination', 'total_time', 'token_bytes']
data = defaultdict(lambda: list())

for f in fields:
    for c in configurations:
        data[f].append(get_box_trace(getattr(c, 'get_'+f)(), c.fault_sensitive))

# for plot_name, plot_data in data.items():
    # plotly.offline.plot(plot_data, filename='../graphs/%s.html' % plot_name)


for configuration in configurations:
    data = get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens(), "tokens")
    #
    #
    data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_number_of_nodes_crashed(), "faults")
    data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_backup_tokens(), "backup")
    data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_tokens_after_termination(), "afterTermination")
    data += get_scatter_graph_with_mean_and_confidence_interval(list(range(len(configuration.repetitions))), configuration.get_safra_time(), "times")
    plotly.offline.plot(data, filename='../graphs/graph.html')

