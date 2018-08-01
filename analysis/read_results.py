import csv
import sys
from collections import defaultdict

from os import listdir

from os.path import isdir, basename, isfile

import plotly
import plotly.graph_objs as go

import scipy.stats as st

from graphing import get_scatter_graph_with_mean_and_confidence_interval, get_box_trace


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
				self.basic_time = float(statistics['basicTime'])

				if 'totalTimeAfterTermination' in statistics:
					self.total_time_after_termination = float(statistics['totalTimeAfterTermination'])
				else:
					self.total_time_after_termination = -1

				if 'numberOfNodesCrashed' in statistics:
					self.number_of_nodes_crashed = int(statistics['numberOfNodesCrashed'])
					real_fault_percentage = self.number_of_nodes_crashed / number_of_nodes
					if real_fault_percentage < fault_percentage - 0.1:
						self.valid = False
						self.errors.append(
							"Fault percentage of %f but aimed for %f" % (real_fault_percentage, fault_percentage))
				break  # There should be only one line

		logs = len(list(filter(lambda f: f.endswith('.log'), listdir(folder))))
		chandy_misra_results = len(list(filter(lambda f: f.endswith('.chandyMisra'), listdir(folder))))

		assert logs == number_of_nodes + 1, folder  # 1 is the out.log file summarizing the whole run
		assert chandy_misra_results == number_of_nodes, folder


class Configuration:

	def __init__(self, repetitions, invalid_repetitions, number_of_nodes, fault_percentage, fault_sensitive, fault_group):
		self.fault_group = fault_group
		self.fault_sensitive = fault_sensitive
		self.fault_percentage = fault_percentage
		self.number_of_nodes = number_of_nodes
		self.invalid_repetitions = invalid_repetitions
		self.repetitions = repetitions

	@classmethod
	def from_folder(cls, folder):
		configuration_name = basename(folder)
		number_of_nodes, fault_percentage, _ = configuration_name.split('-')
		number_of_nodes = int(number_of_nodes)
		if fault_percentage == 'fs':
			fault_sensitive = True
			fault_percentage = 0.0
		else:
			fault_sensitive = False
			fault_percentage = float(fault_percentage) / 100

		if fault_sensitive:
			fault_group = '0 fs'
		elif fault_percentage == 0.0:
			fault_group = '0'
		elif fault_percentage == 0.9:
			fault_group = '90'
		else:
			fault_group = '5n'

		repetitions = []
		invalid_repetitions = []
		for file_name in listdir(folder):
			if isdir('/'.join((folder, file_name))) and not file_name.endswith('.failure'):
				r = Repetition('/'.join((folder, file_name)), number_of_nodes, fault_percentage)
				if r.valid:
					repetitions.append(r)
				else:
					invalid_repetitions.append(r)
		return Configuration(repetitions, invalid_repetitions, number_of_nodes, fault_percentage, fault_sensitive,
		                     fault_group)

	def get_tokens(self):
		return list(map(lambda r: r.tokens, self.repetitions))

	def get_token_bytes(self):
		return list(map(lambda r: r.token_bytes, self.repetitions))

	def get_safra_times(self):
		return list(map(lambda r: r.safra_time, self.repetitions))

	def get_total_times(self):
		return list(map(lambda r: r.total_time, self.repetitions))

	def get_safra_times_after_termination(self):
		return list(map(lambda r: r.safra_time_after_termination, self.repetitions))

	def get_total_times_after_termination(self):
		return list(map(lambda r: r.total_time_after_termination, self.repetitions))

	def get_number_of_nodes_crashed(self):
		return list(map(lambda r: r.number_of_nodes_crashed, self.repetitions))

	def get_backup_tokens(self):
		return list(map(lambda r: r.backup_tokens, self.repetitions))

	def get_tokens_after_termination(self):
		return list(map(lambda r: r.tokens_after_termination, self.repetitions))

	def get_basic_times(self):
		return list(map(lambda r: r.basic_time, self.repetitions))

	def merge_with(self, other):
		assert self.fault_sensitive == other.fault_sensitive
		assert self.number_of_nodes == other.number_of_nodes
		assert self.fault_percentage == other.fault_percentage
		self.repetitions += other.repetitions
		self.invalid_repetitions += other.invalid_repetitions
		return self


def get_configurations(folder):
	configurations = defaultdict(lambda: list())

	for file_name in listdir(folder):
		configuration_folder = '/'.join((folder, file_name))
		if isdir(configuration_folder) and configuration_folder.endswith('.run'):
			configuration = Configuration.from_folder(configuration_folder)
			configurations[(configuration.number_of_nodes, configuration.fault_percentage, configuration.fault_sensitive,
			                configuration.fault_group)].append(configuration)

	merged_configurations = []
	for key_values, similiar_configurations in configurations.items():
		merged = Configuration([], [], *key_values)
		for c in similiar_configurations:
			merged = merged.merge_with(c)
		merged_configurations.append(merged)

	merged_configurations = sorted(merged_configurations, key=lambda c: c.fault_sensitive)
	merged_configurations = sorted(merged_configurations, key=lambda c: c.fault_percentage)
	merged_configurations = sorted(merged_configurations, key=lambda c: c.number_of_nodes)

	return merged_configurations
