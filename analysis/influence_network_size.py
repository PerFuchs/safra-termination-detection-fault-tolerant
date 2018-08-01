import operator
import statistics
from collections import defaultdict, OrderedDict

import plotly
from plotly import graph_objs as go

import graphing

def difference_in_percent(a, b):
    difference = abs(a - b)
    return round(difference / min(a, b) * 100)


def analyse_influence_of_network_size(configurations):
    grouped_by_fault_group = defaultdict(lambda: list())
    for c in configurations:
        grouped_by_fault_group[c.fault_group].append(c)
    grouped_by_fault_percentage_sorted = dict(map(lambda i: (i[0], sorted(i[1], key=lambda c: c.number_of_nodes)), grouped_by_fault_group.items()))
    grouped_by_fault_percentage_sorted = OrderedDict(sorted(grouped_by_fault_percentage_sorted.items(), key=lambda i: i[0]))

    present_token_and_token_after_termination(grouped_by_fault_percentage_sorted)

    analyse_influence_on_safra_time(grouped_by_fault_percentage_sorted)
    analyse_influence_on_safra_time_after_termination(grouped_by_fault_percentage_sorted)
    #
    # analyse_influence_on_basic_time(grouped_by_fault_percentage_sorted)
    # analyse_influence_on_total_time(grouped_by_fault_percentage_sorted)


def present_token_and_token_after_termination(configurations):
    data = []

    for i in range(5):
        fs_configuration = configurations['0 fs'][i]
        ft_configuration = configurations['0'][i]
        data.append(graphing.get_box_trace(fs_configuration.get_tokens(), 'FS %i' % fs_configuration.number_of_nodes))
        data.append(graphing.get_box_trace(fs_configuration.get_tokens_after_termination(), 'FS %i' % fs_configuration.number_of_nodes, 'rgb(255,140,0)'))

        data.append(graphing.get_box_trace(ft_configuration.get_tokens(), 'FT %i' % ft_configuration.number_of_nodes))
        data.append(graphing.get_box_trace(ft_configuration.get_tokens_after_termination(), 'FT %i' % ft_configuration.number_of_nodes, 'rgb(255,140,0'))

    plotly.offline.plot(data, filename='../graphs/tokens_and_token_after_termination_box_plot.html')

def analyse_influence_on_safra_time(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'safra_times', True)


def analyse_influence_on_safra_time_after_termination(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'safra_times_after_termination', True)


def analyse_influence_on_basic_time(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'basic_times', True)


def analyse_influence_on_total_time(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'total_times', True)


def present_linear_relationship(sorted_configurations, field_name, float_representation=False):
    scale = 0
    next_scale = 0
    for c in sorted_configurations:
        mean = statistics.mean(getattr(c, 'get_' +field_name)())
        if scale == 0:
            scale = mean
            next_scale = mean
        else:
            scale = next_scale
            next_scale = mean
        if float_representation:
            print("FG: %s %s: %f Network: %i Scale: %f" % (c.fault_group, field_name, mean, c.number_of_nodes, mean / scale))
        else:
            print("FG: %s %s: %i Network: %i Scale: %f" % (c.fault_group, field_name, mean, c.number_of_nodes, mean / scale))
    print("\n")