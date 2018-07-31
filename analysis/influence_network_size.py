import operator
import statistics
from collections import defaultdict, OrderedDict

import plotly
from plotly import graph_objs as go

import graphing


def present_token_vs_token_after_termination(grouped_by_fault_percentage_sorted):
    headers = ['Tokens FT', 'Tokens FS', 'Difference (%)', 'TA FT', 'TA FS', 'Difference (%)', 'FT ratio', 'FS ratio']

    network_sizes = 4
    values = []
    for i in range(len(headers)):
        values.append([-1] * network_sizes)
    for i in range(network_sizes):
        ft_configuration = grouped_by_fault_percentage_sorted['0'][i]
        fs_configuration = grouped_by_fault_percentage_sorted['0 fs'][i]

        ft_token_mean = round(statistics.mean(ft_configuration.get_tokens()))
        fs_token_mean = round(statistics.mean(fs_configuration.get_tokens()))
        ft_token_after_termination_mean = round(statistics.mean(ft_configuration.get_tokens_after_termination()))
        fs_token_after_termination_mean = round(statistics.mean(fs_configuration.get_tokens_after_termination()))

        token_difference = difference_in_percent(ft_token_mean, fs_token_mean)
        token_after_termination_difference = difference_in_percent(ft_token_after_termination_mean, fs_token_after_termination_mean)

        ft_ratio = round(ft_token_mean / ft_token_after_termination_mean, 2)
        fs_ratio = round(fs_token_mean / fs_token_after_termination_mean, 2)


        row = [ft_token_mean, fs_token_mean, token_difference,
                       ft_token_after_termination_mean,
                       fs_token_after_termination_mean,
                       token_after_termination_difference,
                       ft_ratio,
                       fs_ratio]
        for j, value in enumerate(row):
            values[j][i] = value


    data = [go.Table(
        header=dict(values=headers),
        cells=dict(values=values)
    )]
    plotly.plotly.plot(data, filename='ft_vs_fs_table.html')

    # trace = go.Table(
    #     header=dict(values=['A Scores', 'B Scores']),
    #     cells=dict(values=[[100, 90, 80, 90],
    #                        [95, 85, 75, 95]]))
    #
    # data = [trace]
    # plotly.plotly.plot(data, filename = 'basic_table')



def difference_in_percent(a, b):
    difference = abs(a - b)
    return round(difference / min(a, b) * 100)


def analyse_influence_of_network_size(configurations):
    grouped_by_fault_group = defaultdict(lambda: list())
    for c in configurations:
        grouped_by_fault_group[c.fault_group].append(c)
    grouped_by_fault_percentage_sorted = dict(map(lambda i: (i[0], sorted(i[1], key=lambda c: c.number_of_nodes)), grouped_by_fault_group.items()))
    grouped_by_fault_percentage_sorted = OrderedDict(sorted(grouped_by_fault_percentage_sorted.items(), key=lambda i: i[0]))

    present_linear_relationships_token_token_after_termination(grouped_by_fault_percentage_sorted)
    present_token_vs_token_after_termination(grouped_by_fault_percentage_sorted)

    # analyse_influence_on_tokens(grouped_by_fault_percentage_sorted)
    # analyse_influence_on_tokens_after_termination(grouped_by_fault_percentage_sorted)

    analyse_influence_on_token_bytes(grouped_by_fault_percentage_sorted)

    analyse_influence_on_token_vs_token_after_termination(grouped_by_fault_percentage_sorted)


    analyse_influence_on_safra_time(grouped_by_fault_percentage_sorted)
    analyse_influence_on_safra_time_after_termination(grouped_by_fault_percentage_sorted)

    analyse_influence_on_basic_time(grouped_by_fault_percentage_sorted)
    analyse_influence_on_total_time(grouped_by_fault_percentage_sorted)


def present_linear_relationships_token_token_after_termination(configurations):
    data = []

    for i in range(4):
        fs_configuration = configurations['0 fs'][i]
        ft_configuration = configurations['0'][i]
        data.append(graphing.get_box_trace(fs_configuration.get_tokens(), 'FS %i' % fs_configuration.number_of_nodes))
        data.append(graphing.get_box_trace(fs_configuration.get_tokens_after_termination(), 'FS %i' % fs_configuration.number_of_nodes, 'rgb(255,140,0)'))

        data.append(graphing.get_box_trace(ft_configuration.get_tokens(), 'FT %i' % ft_configuration.number_of_nodes))
        data.append(graphing.get_box_trace(ft_configuration.get_tokens_after_termination(), 'FT %i' % ft_configuration.number_of_nodes, 'rgb(255,140,0'))

    plotly.offline.plot(data, filename='../graphs/tokens_and_token_after_termination_box_plot.html')


# def present_linear_relationships_token_token_after_termination(configurations):
#     data = []
#     x_axis = ['50', '250', '500', '1000', '2000']
#     for fault_group, sorted_configurations in configurations.items():
#         if fault_group == '0' or fault_group == 'fs':
#             data.append(graphing.get_scatter_graph_data(list(map(lambda c: statistics.mean(c.get_tokens()), sorted_configurations)), x_axis, 'Tokens %s' % fault_group))
#             data.append(graphing.get_scatter_graph_data(list(map(lambda c: statistics.mean(c.get_tokens_after_termination()), sorted_configurations)), x_axis, 'Tokens after termination %s' % fault_group))
#     plotly.offline.plot(data, filename='../graphs/tokens_and_token_after_termination_box_plot.html')


def analyse_influence_on_tokens(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'tokens')


def analyse_influence_on_tokens_after_termination(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'tokens_after_termination')


def analyse_influence_on_token_vs_token_after_termination(configurations):
    for fault_group, sorted_configurations in configurations.items():
        for c in sorted_configurations:
            print("FG: %s Token/Token after termination: %f Network: %i" % (c.fault_group,
                                                                            statistics.mean(c.get_tokens()) / statistics.mean(c.get_tokens_after_termination()),
                                                                            c.number_of_nodes))
        print("\n\n")


def analyse_influence_on_token_bytes(configurations):
    for fault_group, sorted_configurations in configurations.items():
        present_linear_relationship(sorted_configurations, 'token_bytes')

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